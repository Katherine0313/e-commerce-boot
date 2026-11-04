# 线上问题排查手册

> 适用：e-commerce-boot 微服务（Docker + Nacos 部署在 `/www/java`）
> 核心原则：**浏览器只给状态码，根因永远在服务端日志里。**

---

## 1. 先找到日志

### 1.1 生产环境

服务以 Docker 容器运行，Spring Boot 日志输出到容器 stdout/stderr，**服务器上没有单独的日志文件**。

```bash
# 登录服务器（本机已配置 SSH 别名）
ssh ecommerce

cd /www/java

docker logs product-service              # 从启动到现在的全部日志
docker logs --tail 100 product-service  # 最近 100 行
docker logs -f product-service          # 实时跟踪（Ctrl+C 退出）
docker logs --since 10m product-service # 最近 10 分钟
```

一条命令直接看（不用登录）：

```bash
ssh ecommerce "docker logs --tail 100 product-service"
ssh ecommerce "cd /www/java && docker compose logs --tail 100 product-service"
```

启动成功的关键行：

```text
The following 1 profile is active: "prod"
Tomcat started on port 9003
Started ProductServiceApplication in x seconds
nacos registry, DEFAULT_GROUP product-service 172.x.x.x:9003 register finished
```

### 1.2 本地开发

本地日志在 IDE 控制台，不在 Docker：

- IDEA → Run/Debug 对应服务的 Console
- 或终端：`mvn -pl product-service spring-boot:run`
- 或：`java -jar product-service/target/product-service-0.0.1-SNAPSHOT.jar`

---

## 2. HTTP 500 排查步骤

### 2.1 不要从栈顶看

Spring MVC / Tomcat 的调用栈很长，顶部基本都是：

```text
at org.springframework.web.servlet.DispatcherServlet...
at org.apache.catalina...
```

这些只说明「请求怎么进来的」，没有诊断价值。

### 2.2 拉到底部找 `Caused by`

日志的**最后一个 `Caused by`** 才是根因，例如：

```text
Caused by: java.sql.SQLSyntaxErrorException: Unknown column 'images' in 'field list'
    at com.mysql.cj.jdbc.exceptions.SQLError.createSQLException(...)
    at com.mysql.cj.jdbc.ClientPreparedStatement.executeInternal(...)
    at org.apache.ibatis.executor.statement.PreparedStatementHandler.query(...)
```

从这三行能直接得出：

- 异常属于 SQL 层（`SQLSyntaxErrorException`），不是空指针、不是业务异常
- MySQL 明确点名缺列：`images`
- `in 'field list'` 说明是 SELECT 字段列表里用了它

### 2.3 交叉验证：代码要什么，数据库有什么

1. 代码侧确认字段确实被引用：Mapper XML / Entity / VO
2. 数据库侧确认列是否存在：

```sql
SHOW COLUMNS FROM promotion_banner;
SHOW COLUMNS FROM promotion_banner LIKE '%images%';

-- 或查信息模式
SELECT COLUMN_NAME, COLUMN_TYPE
FROM information_schema.COLUMNS
WHERE TABLE_SCHEMA = DATABASE()
  AND TABLE_NAME = 'promotion_banner'
  AND COLUMN_NAME = 'images';
```

两边对不上，结论就是：**代码更新了，但生产库没有执行对应的 DDL。**

### 2.4 SQL 错误速查

| 日志关键字 | 含义 |
|-----------|------|
| `Unknown column 'x' in 'field list'` | SELECT 字段缺列 |
| `Unknown column 'x' in 'where clause'` | WHERE 条件缺列 |
| `Unknown column 'x' in 'order clause'` | ORDER BY 字段缺列 |
| `Table 'xxx' doesn't exist` | 缺表 |
| `Unknown database 'xxx'` | 库名或连接配置错误 |
| `Data too long for column` | 字段长度不足 |
| `Cannot add or update a child row` | 外键约束失败 |

---

## 3. 发布后 500 的常见原因

| 原因 | 判断方式 | 处理 |
|------|----------|------|
| 生产库漏执行 DDL | 日志 `Unknown column`，`SHOW COLUMNS` 无该列 | 执行 `docs/sql` 对应脚本 |
| 服务没重启 | 容器 `Up` 时间早于发布时间 | `docker compose up -d --no-deps 服务名` |
| 镜像没更新 | `docker inspect -f '{{.Config.Image}}' 服务名` 是旧 tag | 重新 build/push/pull |
| 缺环境变量 | 启动日志报占位符解析失败 | 补 `deploy/.env` 或 compose 环境变量 |
| 新服务未注册到 Nacos | 网关日志无该服务实例 | 查看服务日志中的 register 行 |

---

## 4. 本次案例复盘（Banner 500）

**现象**

```text
GET https://www.jiemids.com/api/admin/banners?page=1&pageSize=10
500 Internal Server Error
```

**日志**

```text
Caused by: java.sql.SQLSyntaxErrorException: Unknown column 'images' in 'field list'
```

**定位**

- Mapper 的 SELECT 已包含 `images`
- 生产 `promotion_banner` 没有 `images` 列
- 结论：Banner 多图功能上线时，本地执行了 DDL，生产漏了

**修复**

```sql
ALTER TABLE promotion_banner
  ADD COLUMN images TEXT NULL
  COMMENT '图片地址 JSON 数组（最多 4 张，首张与 image 相同）'
  AFTER image;
```

脚本：[docs/sql/20261104_banner_images.sql](sql/20261104_banner_images.sql)

**验证**

```bash
curl -s -o /dev/null -w '%{http_code}\n' http://127.0.0.1:8080/api/banners
# 200
```

老数据不需要回填：`images` 为 NULL 时，`BannerServiceImpl` 自动回落到 `image` 单图。

---

## 5. 常用命令速查

```bash
# 容器状态
docker ps --format '{{.Names}} {{.Status}}'
docker inspect -f '{{.Config.Image}} {{.State.Status}}' product-service

# 日志
docker logs --tail 100 product-service
docker logs --since 10m product-service

# 进入容器
docker exec -it product-service sh

# 数据库（服务器上，读取 /www/java/.env）
cd /www/java && set -a && . ./.env && set +a
mysql -h127.0.0.1 -u"$DB_USERNAME" -p"$DB_PASSWORD" --default-character-set=utf8mb4 e_commerce_db

# 备份
bash deploy/backup-prod-db.sh        # 备份到 /www/backup/
```

---

## 6. 注意事项

- **不要仅凭浏览器 500 猜原因**，先取服务端栈底 `Caused by`
- 生产 MySQL 是 **5.7**，SQL 不要用 `CAST(... AS JSON)` 等 8.0 语法
- Nacos 客户端 `login failed 403` 在关闭鉴权时属于历史噪音，已通过清空默认账号密码消除；若再出现，检查 `NACOS_USERNAME/PASSWORD`
- 本地 Maven 打包时若有服务以 `java -jar` 运行，会锁住 target 下的 jar 导致 `repackage` 失败，先停服务再打包
