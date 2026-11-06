# RBAC 日志异步化方案

> 2026-11-06 编写。梳理当前项目里登录日志、操作日志的写入方式，评估是否需要引入异步管理器，以及如何落地。

---

## 一、现状盘点

### 1.1 操作日志（`admin_operation_log`）—— **已经异步**

写入入口：`common/.../log/JdbcOperationLogRecorder.java`

```java
public void save(OperationLogEntry entry) {
    executor.execute(() -> doSave(entry));   // 提交到线程池，立即返回
}
```

线程池配置：`common/.../log/LogAsyncConfig.java`

| 参数 | 值 | 说明 |
|---|---|---|
| corePoolSize | 2 | 常驻线程 |
| maxPoolSize | 8 | 峰值线程 |
| queueCapacity | 2000 | 队列容量 |
| 拒绝策略 | `CallerRunsPolicy` | 池+队列都满时，调用线程自己写，保证日志不丢 |
| 停机 | `waitForTasksToCompleteOnShutdown=true`，等 10s | 停机前把队列里的日志写完 |

切面 `OperationLogAspect` 在 `finally` 里调 `recorder.save()`，提交到线程池后立刻返回，**不阻塞业务线程**。

### 1.2 登录日志（`admin_login_log`）—— **同步**

写入入口：`auth-service/.../LoginLogServiceImpl.record()`

```java
public void record(...) {
    try {
        AdminLoginLog loginLog = AdminLoginLog.builder()....build();
        adminLoginLogMapper.insert(loginLog);   // 同步写库，阻塞当前线程
    } catch (Exception e) {
        log.warn("写登录日志失败: ...");          // try/catch 兜底，不让登录失败
    }
}
```

调用位置（`AuthServiceImpl.login()`）：

```java
AdminLoginVO result = buildLoginResult(admin, clientIp, userAgent);
recordLoginLog(admin.getId(), admin.getUsername(), LoginResult.SUCCESS, null, clientIp, userAgent);
return result;   // ← 日志写完才返回 token
```

**问题**：日志在 `return result` 之前同步写入。DB 慢、锁等待、网络抖动，都会让用户多等一段时间才拿到 token。虽然 try/catch 保证了"日志失败不影响登录"，但**日志慢会拖慢登录**。

登出日志同理，也是同步。

### 1.3 项目里没有 @Async

全项目搜索 `@Async` / `@EnableAsync` —— **零匹配**。异步统一用 `ThreadPoolTaskExecutor.execute()` 手动提交，不走 Spring 的代理注解。

---

## 二、要不要把登录日志也改成异步？

### 结论：**要**

理由：

1. **登录日志是附属留痕，不是核心业务**。用户要的是 token，不是"日志写成功"。
2. **当前同步写在 return 之前**，DB 抖动直接拉长登录响应时间。后台管理员数量虽少，但登录是高频入口，每个失败尝试也会写日志（爆破场景下量不小）。
3. **操作日志已经走了异步**，登录日志同步会造成两套日志写入语义不一致。统一成异步，后续维护心智负担更低。
4. **故障隔离**：写日志的异常、慢、锁，都不该影响登录/登出的成功与否。try/catch 只能防异常，防不了慢。

### 不改会怎样？

- 登录接口响应时间 = 校验 + 查权限 + 签发 token + **写日志 DB 耗时**
- DB 慢查询或锁等待时，登录接口跟着慢，用户页面转圈
- Tomcat 请求线程被写日志占住，并发上来后排队

---

## 三、实现方案

### 3.1 选型：复用现有模式，不引入 @Async

项目已有 `LogAsyncConfig` + `ThreadPoolTaskExecutor` 的成熟做法（操作日志在用）。登录日志**沿用同一套模式**，有两种子方案：

| 方案 | 做法 | 优点 | 缺点 |
|---|---|---|---|
| **A. 复用 `operationLogExecutor`** | LoginLogServiceImpl 注入同一个线程池 | 少一个 Bean | 操作日志和登录日志共用池，一类打满会影响另一类 |
| **B. 独立 `loginLogExecutor`** | 新增一个线程池 Bean | 两类日志资源隔离，互不影响 | 多一个 Bean，多几行配置 |

**推荐方案 B**。理由：操作日志是业务写操作触发（增删改），登录日志是认证触发，两者流量特征不同。独立池子保证一类打满不拖垮另一类，和 `LogAsyncConfig` 注释里"日志池不和业务池混用"的思路一致。

### 3.2 为什么不用 `@Async`

- 项目全局没有 `@EnableAsync`，引入要改启动类
- `@Async` 走 Spring 代理，同类内调用失效（`this.record()` 不会异步），需要自注入或拆分，容易踩坑
- 现有操作日志用 `execute()` 手动提交，登录日志保持一致更好维护
- `@Async` 的线程池是 Spring 全局 `SimpleAsyncTaskExecutor`（每次新建线程），不配置会有性能隐患

### 3.3 改动清单

#### ① common 层：新增登录日志线程池

`common/src/main/java/ecommerce/log/LogAsyncConfig.java` 追加一个 Bean：

```java
@Bean("loginLogExecutor")
public ThreadPoolTaskExecutor loginLogExecutor() {
    ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
    executor.setCorePoolSize(1);
    executor.setMaxPoolSize(4);
    executor.setQueueCapacity(1000);
    executor.setThreadNamePrefix("login-log-");
    executor.setRejectedExecutionHandler(new ThreadPoolExecutor.CallerRunsPolicy());
    executor.setWaitForTasksToCompleteOnShutdown(true);
    executor.setAwaitTerminationSeconds(10);
    return executor;
}
```

> 登录日志量比操作日志小，core=1/max=4/queue=1000 足够。拒绝策略同样 `CallerRuns`，保证极端情况不丢日志。

#### ② auth-service：LoginLogServiceImpl 改异步

```java
@Slf4j
@Service
public class LoginLogServiceImpl implements LoginLogService {

    private final AdminLoginLogMapper adminLoginLogMapper;
    private final LogExportSupport logExportSupport;
    private final ThreadPoolTaskExecutor loginLogExecutor;

    public LoginLogServiceImpl(AdminLoginLogMapper adminLoginLogMapper,
                                LogExportSupport logExportSupport,
                                @Qualifier("loginLogExecutor") ThreadPoolTaskExecutor loginLogExecutor) {
        this.adminLoginLogMapper = adminLoginLogMapper;
        this.logExportSupport = logExportSupport;
        this.loginLogExecutor = loginLogExecutor;
    }

    @Override
    public void record(Long adminId, String username, LoginType type, LoginResult result,
                       String reason, String ip, String userAgent) {
        // 构造对象（在调用线程做，避免异步线程拿不到请求上下文）
        AdminLoginLog loginLog = AdminLoginLog.builder()
                .adminId(adminId)
                .username(username == null ? "" : username)
                .loginType(type == null ? LoginType.LOGIN.name() : type.name())
                .resultCode(result == null ? LoginResult.SUCCESS.name() : result.name())
                .reason(truncate(reason, MAX_REASON_LENGTH))
                .ip(truncate(ip, MAX_IP_LENGTH))
                .userAgent(truncate(userAgent, MAX_USER_AGENT_LENGTH))
                .createdAt(LocalDateTime.now())
                .build();
        // 提交到线程池，立即返回
        try {
            loginLogExecutor.execute(() -> doSave(loginLog));
        } catch (Exception e) {
            log.warn("登录日志提交线程池失败: username={}, result={}, err={}",
                    username, result, e.getMessage());
        }
    }

    private void doSave(AdminLoginLog loginLog) {
        try {
            adminLoginLogMapper.insert(loginLog);
        } catch (Exception e) {
            log.warn("写登录日志失败: username={}, err={}", loginLog.getUsername(), e.getMessage());
        }
    }
    // ... page / export 方法不变
}
```

**关键点**：
- `AdminLoginLog` 对象在**调用线程**构造好（`username`、`ip`、`userAgent` 这些都从请求里取，异步线程拿不到 `RequestContextHolder`）
- `execute()` 提交后立即返回，登录接口不再等写库
- `doSave` 内部 try/catch，写失败只 warn
- 外层再套一层 try/catch 防线程池提交失败（极端情况）

#### ③ AuthServiceImpl.login() 无需改动

调用 `recordLoginLog(...)` 的代码不动，异步封装在 `LoginLogServiceImpl` 内部。`return result` 不再被日志写库阻塞。

---

## 四、注意事项

### 4.1 异步后事务语义

登录日志和业务**不在同一个事务**里（本来也不在）。异步后更明确：业务成功/失败与日志写入完全解耦。审计日志允许"业务回滚但日志已写"，符合审计语义。

### 4.2 线程池监控

可通过 Spring Boot Actuator 或日志观察线程池状态。当前 `operationLogExecutor` 没有暴露监控指标，`loginLogExecutor` 同样。后续如需监控，可接入 Micrometer。

### 4.3 停机丢日志风险

`waitForTasksToCompleteOnShutdown=true` + `awaitTerminationSeconds=10`，停机时最多等 10 秒把队列写完。如果 10 秒内写不完，队列里剩余的日志会丢。对于后台系统可接受。

### 4.4 不引入 @Async 的约束

如果后续有人想加 `@Async`，必须：
1. 启动类加 `@EnableAsync`
2. 不能在同类内直接调用（会绕过代理）
3. 指定线程池：`@Async("loginLogExecutor")`，否则用 Spring 默认的 `SimpleAsyncTaskExecutor`

**当前阶段不建议引入**，保持和操作日志一致的 `execute()` 模式。

---

## 五、实施步骤

| 步骤 | 内容 | 验证 |
|---|---|---|
| 1 | `LogAsyncConfig` 加 `loginLogExecutor` Bean | 启动无报错 |
| 2 | `LoginLogServiceImpl` 注入线程池，`record()` 改 `execute()` | 登录接口响应时间下降（去掉写库耗时） |
| 3 | 验证登录成功 / 失败各分支日志正常写入 | DB 有对应记录 |
| 4 | 验证写日志异常不影响登录 | 故意让 insert 失败，登录仍成功 |
| 5 | 构建 + 部署 auth-service | 生产验证 |

---

## 六、总结

| 日志类型 | 当前 | 改造后 |
|---|---|---|
| 操作日志 | 异步（`operationLogExecutor`） | 不变 |
| 登录日志 | 同步，阻塞 return | 异步（`loginLogExecutor`） |
| 登出日志 | 同步 | 异步（复用登录日志逻辑） |

**核心改动只有 2 个文件**：`LogAsyncConfig.java` 加一个线程池 Bean，`LoginLogServiceImpl.java` 把 `insert` 包进 `executor.execute()`。不动调用方、不动表结构、不动前端。

需要我开始实施吗？

---

## 附录：操作日志学习路线（从浅入深）

> 给刚接触操作日志的同学。按顺序看，每一步都能在项目里找到对应代码。

### 第一步：从「打了注解的地方」开始看（最直观）

操作日志的入口是一个注解 `@OperationLog`。先找一个最简单的用法，比如管理员账号的新增：

`auth-service/src/main/java/ecommerce/auth/service/impl/AdminAccountServiceImpl.java`

你会看到方法上写着类似：

```java
@OperationLog(module = "system", action = "USER_ADD", bizId = "#result.id", detail = "新增管理员账号")
public Admin create(...) { ... }
```

**这一步要搞懂的**：注解的几个字段是什么意思——`module`（哪个模块）、`action`（做了什么动作）、`bizId`（操作的业务对象ID，用 SpEL 表达式取返回值）、`detail`（描述）。

**目标**：知道"想给某个方法记操作日志，就加这个注解"。

### 第二步：看注解定义本身

`common/src/main/java/ecommerce/log/OperationLog.java`

就是一个 `@interface`，定义了有哪些参数、默认值是什么。看完你就知道第一步里每个字段能传什么。

### 第三步：看切面（核心，注解怎么生效的）

`common/src/main/java/ecommerce/log/OperationLogAspect.java`

这是**最关键的一个文件**。它用 `@Around("@annotation(operationLog)")` 拦截所有打了 `@OperationLog` 的方法。流程是：

```
方法执行前 → 记录开始时间
  → 执行业务方法（proceed()）
  → 成功/失败
方法执行后（finally）→ 组装日志对象 → 调 recorder.save() → 清理 ThreadLocal
```

**重点看这几行**：
- `joinPoint.proceed()` —— 执行业务方法本身
- `finally` 块里的 `recorder.save(buildEntry(...))` —— 不管成功失败都记日志
- `LogDetail.clear()` —— 清理线程变量（不清理会串到下一个请求）

**目标**：理解 AOP 切面的"包裹"逻辑——业务方法在中间，日志在 finally 里兜底。

### 第四步：看日志数据从哪来（Collector）

`common/src/main/java/ecommerce/log/OperationLogCollector.java`

切面里 `buildEntry()` 调用了很多 `collector.xxx()` 方法来取数据：
- `currentUserId()` —— 当前操作人是谁（从 ThreadLocal 的 UserContext 取）
- `clientIp(request)` —— 客户端 IP
- `paramsJson(args)` —— 方法参数转 JSON（还做了密码脱敏）

**目标**：理解日志里的"谁在什么时间什么IP操作了什么"这些信息分别从哪来。

### 第五步：看日志怎么存（Recorder）

两个文件一起看：

- `common/src/main/java/ecommerce/log/OperationLogRecorder.java` —— 接口，只有一个 `save()` 方法
- `common/src/main/java/ecommerce/log/JdbcOperationLogRecorder.java` —— 实现类

重点看 `save()` 方法：

```java
public void save(OperationLogEntry entry) {
    executor.execute(() -> doSave(entry));  // 丢给线程池，异步写库
}
```

**目标**：理解"日志组装好之后，不直接写库，而是丢给线程池异步写"。这里就是本方案讨论的异步点。

### 第六步：看线程池配置（异步的底层）

`common/src/main/java/ecommerce/log/LogAsyncConfig.java`

就一个 Bean，配了线程池的核心线程数、最大线程数、队列容量、拒绝策略。

**目标**：知道异步写日志用的是一个独立线程池，和业务线程不混用。

### 第七步：看变更详情（LogDetail + LogDiff，进阶）

这部分是"操作了什么具体内容"的描述，比较绕，放最后看：

- `common/src/main/java/ecommerce/log/LogDetail.java` —— ThreadLocal 累加器，Service 里往里塞变更文案
- `common/src/main/java/ecommerce/log/LogDiff.java` —— 格式化"XX 从 A 改成 B"

**目标**：理解为什么日志的 detail 字段能写出"销售价从 199 改为 179"这种人话。

### 学习顺序总结

```
注解用法（怎么标记）
  → 注解定义（能传什么）
    → 切面（怎么拦截，核心）
      → Collector（数据从哪来）
        → Recorder（怎么存）
          → 线程池（异步）
            → LogDetail/LogDiff（变更描述，进阶）
```

### 建议的实操方法

1. **先看切面那个文件**，对着上面的流程图读，搞懂"包裹"逻辑
2. **找一个打了注解的方法**（比如 AdminAccountServiceImpl.create），在切面的 `finally` 里打断点，debug 跑一次新增管理员，看 `buildEntry` 组装出来的对象长什么样
3. 看数据库 `admin_operation_log` 表，对照字段理解每个值从哪来
4. 最后再看异步部分
