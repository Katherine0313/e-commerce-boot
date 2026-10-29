# 下单幂等（createOrder 幂等键）实现总结

> 目的：小程序上线后，防止弱网重试 / 用户双击导致「同一笔下单」被重复创建订单、重复扣库存。
> 核心思路：给下单接口加一个 `idempotencyKey`（幂等键），一次「下单意图」复用同一个 key，后端用 Redis `SETNX` 占位保证同一个 key 只真正下单一次。

## 一、动用了哪些文件（共 3 个，全在 order-service）

| 文件 | 改动 |
|---|---|
| `order-service/src/main/java/ecommerce/order/dto/CreateOrderDTO.java` | 加 `idempotencyKey` 字段 |
| `order-service/src/main/java/ecommerce/order/service/impl/OrderServiceImpl.java` | 注入 Redis、加幂等逻辑、抽 `doCreateOrder`、加 4 个降级方法 |
| `order-service/src/main/resources/application.yml` | 补 `spring.data.redis` 连接配置 |

> 注意：`StringRedisTemplate` 是 Spring Boot 自动配置提供的（common 里已引入 `spring-boot-starter-data-redis`），order-service 依赖 common，无需新建 RedisConfig。

---

## 二、业务逻辑（完整流程）

`idempotencyKey` = 一次「下单意图」的标识，**不是**一次 HTTP 请求的标识。

- 用户发起一次下单时，前端生成一个 UUID 作为 key；这次下单的所有重试（弱网重发、超时重试）都复用同一个 key。
- 下单**成功返回后，前端立即丢弃**这个 key；下次再下单（哪怕买同样的商品）重新生成。

后端 `createOrder` 的处理顺序：

```
1. 校验基础字段 + idempotencyKey 非空（空则 400「缺少幂等键」）
2. 拼 Redis key = order:idempotency:{userId}:{idempotencyKey}
3. 读 Redis：
   - 命中 "PENDING"  → 409「订单处理中」     （有请求正在处理，避免并发重复下单）
   - 命中 orderId     → 直接返回该订单 VO      （之前已成功，getOrder 内部校验归属）
   - 未命中           → 继续
4. SETNX 占位 Redis key = "PENDING"（TTL 24h）
   - 占位失败 → 409「订单处理中」（并发竞争，毫秒级，几乎不触发）
   - 占位成功 → 继续
5. 走原下单逻辑（doCreateOrder）：查商品 → 校验库存 → 乐观锁扣库存 → 写订单主表 + 明细
6. 成功：Redis key 改写成 orderId，返回 VO
7. 失败：删除占位 key，重新抛出异常（允许用户下次重试重新下单）
```

**Redis key 设计**：

| 项 | 值 |
|---|---|
| key | `order:idempotency:{userId}:{idempotencyKey}` |
| value | `PENDING`（处理中）或 `orderId`（已成功） |
| TTL | 24 小时（覆盖 30 分钟待付款超时 + 用户中途退出重进） |

key 带 `userId`，天然隔离不同用户（越权拿不到别人的 key）。

**两个关键设计点**：

1. **为什么用「占位」而不是「查有没有缓存」**：两个相同 key 的请求**同时**到达时，缓存里还没有结果。用 `SETNX`（不存在才写入）占位 `PENDING`，只有第一个请求占位成功、真正下单，第二个请求读到 `PENDING` 直接返回「处理中」，不会穿透去重复下单。
2. **失败要删占位**：如果下单过程抛异常（库存不足等），占位还留着的话，用户下次重试会永远命中「处理中」被卡死。所以 catch 到异常要删除占位再抛出。

---

## 三、关键代码

### 1. `CreateOrderDTO.java`（加字段）

```java
@Schema(description = "幂等键：同一次下单意图复用同一值（前端生成 UUID），下单成功后前端丢弃")
private String idempotencyKey;
```

### 2. `OrderServiceImpl.java`

**（a）常量与依赖注入**

```java
// 常量
private static final String IDEMPOTENCY_KEY_PREFIX = "order:idempotency:";
private static final String IDEMPOTENCY_PENDING = "PENDING";
private static final Duration IDEMPOTENCY_TTL = Duration.ofHours(24);

// 构造函数注入
private final StringRedisTemplate stringRedisTemplate;

public OrderServiceImpl(OrderMapper orderMapper, OrderItemMapper orderItemMapper, ProductMapper productMapper,
                        StringRedisTemplate stringRedisTemplate) {
    this.orderMapper = orderMapper;
    this.orderItemMapper = orderItemMapper;
    this.productMapper = productMapper;
    this.stringRedisTemplate = stringRedisTemplate;
}
```

**（b）`createOrder`：加幂等逻辑，原下单逻辑抽到 `doCreateOrder`**

```java
@Override
@Transactional
public OrderVO createOrder(CreateOrderDTO dto) {
    // ...原有 consignee / phone / items 校验...
    if (dto.getIdempotencyKey() == null || dto.getIdempotencyKey().isBlank()) {
        throw new BusinessException(HttpStatus.BAD_REQUEST, "缺少幂等键");
    }

    Long userId = currentUserId();
    String redisKey = IDEMPOTENCY_KEY_PREFIX + userId + ":" + dto.getIdempotencyKey();

    // 命中已有结果：处理中直接提示，已成功则返回原订单（getOrder 内部校验归属）
    String existing = safeGet(redisKey);
    if (existing != null) {
        if (IDEMPOTENCY_PENDING.equals(existing)) {
            throw new BusinessException(HttpStatus.CONFLICT, "订单处理中，请稍后查看订单列表");
        }
        return getOrder(Long.parseLong(existing));
    }

    // SETNX 占位：并发下同一 key 只有第一个请求能占位成功，防止重复下单
    if (!safeSetIfAbsent(redisKey, IDEMPOTENCY_PENDING, IDEMPOTENCY_TTL)) {
        throw new BusinessException(HttpStatus.CONFLICT, "订单处理中，请稍后查看订单列表");
    }

    try {
        OrderVO vo = doCreateOrder(dto, userId);
        // 占位改写为订单 ID，后续相同 key 直接返回该订单
        safeSet(redisKey, String.valueOf(vo.getId()), IDEMPOTENCY_TTL);
        return vo;
    } catch (RuntimeException e) {
        // 下单失败删除占位，允许用户下次重试重新下单
        safeDelete(redisKey);
        throw e;
    }
}
```

**（c）`doCreateOrder`：原有下单逻辑原样搬进来**

```java
private OrderVO doCreateOrder(CreateOrderDTO dto, Long userId) {
    // 逐个查商品，校验库存，组装明细，算总额（略，与原 createOrder 一致）
    // ...
    // 扣库存（乐观锁 stock >= quantity 防超卖）
    // 写订单主表（初始状态：待付款）
    // 写订单明细（回填 orderId）
    return getOrder(order.getId());
}
```

**（d）4 个 `safe*` 降级方法（Redis 挂了不阻断下单）**

```java
private String safeGet(String key) {
    try {
        return stringRedisTemplate.opsForValue().get(key);
    } catch (Exception e) {
        log.warn("幂等键读取失败，降级跳过幂等校验: key={}", key, e);
        return null;   // 视为无缓存，走正常下单
    }
}

private boolean safeSetIfAbsent(String key, String value, Duration ttl) {
    try {
        return Boolean.TRUE.equals(stringRedisTemplate.opsForValue().setIfAbsent(key, value, ttl));
    } catch (Exception e) {
        log.warn("幂等键占位失败，降级视为占位成功: key={}", key, e);
        return true;   // 视为占位成功，继续下单
    }
}

private void safeSet(String key, String value, Duration ttl) {
    try {
        stringRedisTemplate.opsForValue().set(key, value, ttl);
    } catch (Exception e) {
        log.warn("幂等键写结果失败，忽略: key={}", key, e);
    }
}

private void safeDelete(String key) {
    try {
        stringRedisTemplate.delete(key);
    } catch (Exception e) {
        log.warn("幂等键删除失败，忽略: key={}", key, e);
    }
}
```

> 降级语义：Redis 宕机时 `safeGet` 返回 null、`safeSetIfAbsent` 返回 true，等于**幂等自动失效、退化为普通下单**，交易不被 Redis 阻断。

### 3. `application.yml`（补 Redis 连接）

```yaml
spring:
  # ...
  data:
    redis:
      host: ${REDIS_HOST:127.0.0.1}
      port: ${REDIS_PORT:6379}
```

---

## 四、前端必须同步的约定

`idempotencyKey` 现在是**必填**（不传直接 400），前端要配合：

1. 用户发起下单时生成 `idempotencyKey = UUID`（小程序 `crypto.randomUUID()`）。
2. 下单成功 → 立即丢弃 key，跳支付/订单页。
3. 下单失败/超时 → 重试时**复用同一个 key**。
4. 用户取消、改地址/商品重新提交 → **重新生成** key。

## 五、已知边界（可接受，暂不处理）

- **Redis 与 DB 时序**：Redis 写 `orderId` 在 DB 事务提交前。极端情况 DB 提交失败、Redis 却写了 orderId，下次命中后 `getOrder` 查不到订单抛「订单不存在」。概率极低，且不会超卖（库存乐观锁兜底）。
- **并发窗口**：两个相同 key 同时到达，第二个请求读到 `PENDING` 返回 409「处理中」，前端提示「稍后查看订单列表」即可，不必轮询。
