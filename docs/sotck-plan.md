# 库存管理模块设计方案

> 已确认：1 SKU 预警用 ALTER ADD | 2 stock 合并到 product-service | 3 同库+重试+异常标记 | 4 改旧 StockList.vue | 5 无草稿 | 6 关掉手动改 stock

---

## 一、问题

order-service / aftersale-service 直接 `UPDATE product_sku SET stock = stock ± N`，**无流水、无单据、出问题没法追溯**。`stock_alert` 是 product 级预警，SKU 改造后粒度不够。

---

## 二、表结构

### 2.1 ALTER product_sku（预警阈值，方案 A）

```sql
ALTER TABLE product_sku
  ADD COLUMN min_stock INT NOT NULL DEFAULT 0 COMMENT '安全库存（0=不预警）',
  ADD COLUMN max_stock INT NULL COMMENT '上限阈值（可选）';

-- 旧 product 级预警迁移：同一商品下所有 SKU 继承
UPDATE product_sku sku
INNER JOIN stock_alert sa ON sa.product_id = sku.product_id AND sa.alert_enabled = 1
SET sku.min_stock = sa.min_stock;
```

### 2.2 stock_transaction（审计唯一来源）

```sql
CREATE TABLE stock_transaction (
  id BIGINT NOT NULL AUTO_INCREMENT,
  sku_id BIGINT NOT NULL,
  sku_code VARCHAR(64),
  sku_name VARCHAR(200),
  spec_text VARCHAR(200),
  order_item_id BIGINT,                 -- 关联订单项/售后项
  stock_order_id BIGINT,                -- 关联库存单据
  type VARCHAR(20) NOT NULL,            -- IN / OUT / ADJUST
  reason VARCHAR(30) NOT NULL,          -- 销售出库/采购入库/退货归还/盘点调整/手工补录
  qty INT NOT NULL,                     -- 正数，变动量
  balance_after INT NOT NULL,           -- 结存快照（审计关键）
  operator_id BIGINT, operator_name VARCHAR(64),
  remark VARCHAR(500),
  create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (id),
  KEY idx_sku_time (sku_id, create_time),
  KEY idx_order_item (order_item_id),
  KEY idx_order (stock_order_id),
  KEY idx_type_time (type, create_time)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='库存流水';
```

### 2.3 stock_order + stock_order_item

```sql
-- 单据主表
CREATE TABLE stock_order (
  id BIGINT NOT NULL AUTO_INCREMENT,
  order_no VARCHAR(32) NOT NULL,         -- RK/CK/PD + yyyyMMdd + 4位序号
  type VARCHAR(20) NOT NULL,             -- RECEIPT / SHIPMENT / ADJUST
  biz_type VARCHAR(30),                  -- 采购入库/退货归还/销售出库/盘点/手工补录
  related_no VARCHAR(64),                -- 关联订单号/售后单号
  status TINYINT NOT NULL DEFAULT 1,     -- 1已完成 / 0已取消（无草稿）
  total_qty INT NOT NULL DEFAULT 0,
  total_amount DECIMAL(12,2),            -- 采购入库成本总额（可选）
  remark VARCHAR(500),
  operator_id BIGINT, operator_name VARCHAR(64),
  create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  update_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (id), UNIQUE KEY uk_no (order_no),
  KEY idx_related (related_no), KEY idx_type_time (type, create_time)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='库存单据主表';

-- 单据明细
CREATE TABLE stock_order_item (
  id BIGINT NOT NULL AUTO_INCREMENT,
  order_id BIGINT NOT NULL, sku_id BIGINT NOT NULL,
  sku_code VARCHAR(64), sku_name VARCHAR(200), spec_text VARCHAR(200),
  qty INT NOT NULL, cost_price DECIMAL(10,2),
  PRIMARY KEY (id), KEY idx_order (order_id), KEY idx_sku (sku_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='库存单据明细';
```

**不动的表**：`product_sku.stock` 保留，继续做聚合冗余。流水是源数据，stock 是结果，两者在**同一个 Spring 事务**里更新。

---

## 三、后端实现（合并到 product-service）

Gateway 不用改（`/api/admin/stock/**` 已在 product-service 路由下）。旧 `AdminStockController`（product 级预警）**直接删掉**。

### 3.1 product-service 新增目录

```
product-service/src/main/java/ecommerce/product/
  ├ controller/
  │   ├ StockInternalController.java    ★ /internal/stock-write（order/aftersale 调）
  │   ├ StockSkuController.java          /api/admin/stock/skus + 阈值设置
  │   ├ StockOrderController.java         CRUD + complete + cancel
  │   ├ StockTransactionController.java   流水分页查询
  │   └ StockDashboardController.java    看板聚合
  ├ mapper/stock/
  │   ├ StockProductSkuMapper.xml        ★ 行锁读 + 直接 UPDATE + 刷新 products.stock
  │   ├ StockTransactionMapper.xml
  │   ├ StockOrderMapper.xml
  │   └ StockOrderItemMapper.xml
  ├ service/
  │   ├ StockWriteService.java           ★ write() 唯一出入库入口
  │   ├ StockOrderService.java           create / complete
  │   └ StockDashboardService.java
  ├ entity / dto / vo ...
  └ support/OrderNoGenerator.java
```

### 3.2 StockWriteService.write() — 核心方法

```java
@Transactional(rollbackFor = Exception.class)
public void write(StockWriteDTO dto) {
    // 1. 行锁读 SKU（和 UPDATE 同事务防并发）
    ProductSkuLite sku = skuMapper.lockById(dto.getSkuId());  // SELECT ... FOR UPDATE
    if (sku == null) throw new BusinessException("SKU " + dto.getSkuId() + " 不存在");

    // 2. 算方向
    int delta;
    switch (dto.getType()) {
        case "IN":     delta = dto.getQty(); break;
        case "OUT":    delta = -dto.getQty(); break;
        case "ADJUST": delta = dto.getQty(); break;
        default: throw new BusinessException("未知类型: " + dto.getType());
    }
    int newStock = sku.getStock() + delta;
    if ("OUT".equals(dto.getType()) && newStock < 0)
        throw new BusinessException("SKU " + sku.getSkuCode() + " 库存不足");

    // 3. UPDATE product_sku（直接赋值，delta 已在 Java 算好）
    skuMapper.updateStockDirect(dto.getSkuId(), newStock);

    // 4. 刷新商品级汇总（products.stock = SUM(sku.stock) WHERE status=1）
    skuMapper.refreshProductStock(sku.getProductId());

    // 5. 写流水（带结存快照，审计关键）
    StockTransaction tx = new StockTransaction();
    tx.setSkuId(dto.getSkuId()); tx.setSkuCode(sku.getSkuCode());
    tx.setOrderItemId(dto.getOrderItemId()); tx.setStockOrderId(dto.getStockOrderId());
    tx.setType(dto.getType()); tx.setReason(dto.getReason());
    tx.setQty(Math.abs(delta)); tx.setBalanceAfter(newStock);
    tx.setOperatorId(dto.getOperatorId()); tx.setOperatorName(dto.getOperatorName());
    tx.setRemark(dto.getRemark());
    transactionMapper.insert(tx);

    // 6. 单据驱动时刷新 total_qty
    if (dto.getStockOrderId() != null) orderMapper.refreshTotal(dto.getStockOrderId());
}
```

### 3.3 内部接口

```java
@RestController
@RequestMapping("/internal")
public class StockInternalController {
    private final StockWriteService stockWriteService;

    @PostMapping("/stock-write")
    public Result<Void> write(@RequestBody StockWriteDTO dto,
                              @RequestHeader("X-Internal-Call") String internal) {
        if (!"true".equals(internal)) throw new BusinessException("非法内部调用");
        stockWriteService.write(dto);
        return Result.success();
    }
}
```

product-service JwtInterceptor 白名单加 `/internal/**`。

### 3.4 单据号生成

```java
@Component
public class OrderNoGenerator {
    // RK 入库 / CK 出库 / PD 调整 + yyyyMMdd + 4位序号
    public String generate(String type) {
        String prefix = Map.of("RECEIPT","RK","SHIPMENT","CK","ADJUST","PD").get(type);
        String date = LocalDate.now().format(DateTimeFormatter.ofPattern("yyyyMMdd"));
        Integer maxSeq = jdbc.queryForObject(
            "SELECT MAX(CAST(SUBSTRING(order_no, 10, 4) AS SIGNED)) " +
            "FROM stock_order WHERE order_no LIKE ?",
            Integer.class, prefix + date + "%");
        int seq = (maxSeq == null ? 0 : maxSeq) + 1;
        return prefix + date + String.format("%04d", seq);
    }
}
```

---

## 四、order-service / aftersale-service 对接

**方式**：HTTP 调 product-service `/internal/stock-write`，Header `X-Internal-Call: true`。

同库跨服务失败处理（3 次自动重试 + 失败标记 "库存异常待处理" + 定时任务扫）：

| 现有动作 | 现在 | 改后 |
|---|---|---|
| 订单支付 → 扣 SKU | `deductStock` + `refreshProductStock` | **删掉**，改调 `/internal/stock-write`(OUT, 销售出库) |
| 售后退款 → 回补 SKU | `restoreSkuStock` + `refreshProductStock` | **删掉**，改调 `/internal/stock-write`(IN, 退货归还) |
| 售后回补商品级（旧数据兜底） | `restoreStock` | **保留**，老订单没 sku_id 时兜底 |
| 商品编辑手动改 stock | 直接 UPDATE product_sku | **关掉** — 前端只读 + 后端校验拒绝 |

order-service 扣减改造示例：
```java
// RestTemplate 已配置，lb://product-service
StockWriteDTO dto = StockWriteDTO.builder()
    .skuId(sku.getId()).skuCode(sku.getSkuCode())
    .type("OUT").reason("销售出库").qty(quantity).orderItemId(orderItem.getId())
    .build();

int attempts = 0; Exception err = null;
while (attempts++ < 3) {
    try {
        restTemplate.postForEntity("http://product-service/internal/stock-write",
            dto, Void.class, new HttpHeaders() {{ set("X-Internal-Call","true"); }});
        err = null; break;
    } catch (Exception e) { err = e; Thread.sleep(500L * attempts); }
}
if (err != null) orderService.markStockException(orderId);
```

---

## 五、单据类型与生命周期

```
入库单 RECEIPT
  biz_type: 采购入库 / 退货归还(自动) / 手工补录
  完成动作: write(IN, ...)

出库单 SHIPMENT
  biz_type: 销售出库(自动) / 手工补录
  完成动作: write(OUT, ...)

调整单 ADJUST
  biz_type: 盘点调整 / 手工补录
  每行填调整后数量 → 系统算差异
  after > current → write(IN, 差异量)
  after < current → write(OUT, |差异|)
```

P0 无草稿（status 只有 1=已完成 / 0=已取消）。

---

## 六、前端菜单与页面

### 6.1 菜单（扩展现有 `/stock` 一级菜单）

```
库存管理（已有一级，path=/stock, icon=InboxOutlined）
  ├ 库存看板    /stock/dashboard
  ├ SKU 库存    /stock/stock-list（改旧 StockList.vue 为 SKU 级）
  ├ 库存流水    /stock/transactions
  ├ 入库单      /stock/receipts
  ├ 出库单      /stock/shipments
  └ 调整单      /stock/adjustments
```

### 6.2 菜单 SQL

```sql
-- Step1 查一级菜单 id
SELECT id AS parent FROM admin_menu WHERE path = '/stock' AND menu_type = 'M';
-- 假设 parent = X

-- Step2 改旧"库存列表"子菜单标题
UPDATE admin_menu SET menu_name = 'SKU 库存', perms = 'product:sku-stock:list'
WHERE parent_id = X AND menu_name = '库存列表';

-- Step3 插 5 个新子菜单
INSERT INTO admin_menu (parent_id, menu_name, menu_type, path, perms, icon, sort_order, visible, status)
VALUES
  (X,'库存看板','C','dashboard','stock:dashboard:view','DashboardOutlined',1,1,1),
  (X,'库存流水','C','transactions','stock:transaction:view','UnorderedListOutlined',3,1,1),
  (X,'入库单','C','receipts','stock:receipt:*','DownloadOutlined',4,1,1),
  (X,'出库单','C','shipments','stock:shipment:*','UploadOutlined',5,1,1),
  (X,'调整单','C','adjustments','stock:adjust:*','EditOutlined',6,1,1);

-- Step4 角色分配（先拿新菜单 id 再分配给 1/2/4，客服3不给）
```

### 6.3 看板

```
4 卡: SKU 总数 | 今日入库 | 今日出库 | 低库存 SKU 数
低库存 TOP20 表格（商品名/SKU规格/当前/安全/设阈值）
近30天出入库趋势 ECharts 双柱图
```

### 6.4 SKU 库存页（改旧 StockList.vue）

接口从旧 `/api/admin/stock`（product 级）改为 `/api/admin/stock/skus`（SKU 级）。一个商品展开多行，每行一个 SKU。新增列：SKU编码、规格组合、当前库存、安全阈值、预警状态（售罄/低/高/正常）。操作列：设置阈值。

### 6.5 其他页面

**流水页**：过滤（SKU/类型/原因/时间/关联单号）+ 点击关联单号跳转订单/售后详情。

**入库单**：选类型 → 明细行（SKU搜索 → 填数量 → 成本价可选） → 提交即完成。

**调整单**：明细行填调整后数量，系统自动算差异方向。

**出库单列表**：主要看自动生成的销售出库单，点关联订单号跳转。

### 6.6 关掉手动改 stock

前端 AddGoods.vue / EditModal.vue 里 stock 输入框改只读 + 提示文字。后端 product-service 商品保存接口加校验：如果传了 stock 且和 DB 当前值不同，返回 400。

---

## 七、API 清单

### 内部接口（不走网关、不走 JWT）

| 方法 | 路径 | 说明 |
|---|---|---|
| POST | `/internal/stock-write` | 唯一出入库入口，Header `X-Internal-Call: true` |

### 对外管理接口（走网关 `/api/admin/stock/**`）

**SKU 库存**：GET `/api/admin/stock/skus`（分页+搜索+低库存过滤） | POST `/api/admin/stock/skus/{skuId}/alert`（设阈值）

**看板**：GET `/api/admin/stock/dashboard`（聚合） | GET `/api/admin/stock/dashboard/low-stock?limit=20` | GET `/api/admin/stock/dashboard/trend?days=30`

**流水**：GET `/api/admin/stock/transactions`（分页+skuId/type/reason/时间过滤）

**单据**：GET `/api/admin/stock/orders`（列表） | GET `/api/admin/stock/orders/{id}`（详情） | POST `/api/admin/stock/orders`（创建） | POST `/api/admin/stock/orders/{id}/complete`（完成） | POST `/api/admin/stock/orders/{id}/cancel`（取消）

---

## 八、发布顺序

```
1 DB
   ALTER product_sku ADD min_stock / max_stock
   CREATE stock_transaction / stock_order / stock_order_item
   UPDATE product_sku SET min_stock = (从 stock_alert 迁移)

2 product-service
   删旧 AdminStockController（product 级预警）
   加 StockProductSkuMapper + StockWriteService
   加 StockInternalController（JWT 白名单放行 /internal/**）
   加 StockOrderService + StockOrderController
   加 StockTransactionController + StockDashboardController + StockSkuController
   Gateway 不动！(/api/admin/stock/** 已路由)
   编译 → 部署 → 验证

3 order-service
   删 deductStock + refreshProductStock 调用
   加 RestTemplate + 自动重试 3 次 + 异常标记
   编译 → 部署 → 验证下单自动扣库存 + 写流水

4 aftersale-service
   删 restoreSkuStock + refreshProductStock 调用
   同方式加 RestTemplate 调 /internal/stock-write
   旧 restoreStock(product 级兜底) 保留
   编译 → 部署 → 验证售后自动回补 + 写流水

5 前端后台
   router /stock children 加 5 路由
   改旧 StockList.vue → SKU 级
   新建 Dashboard / Transactions / Receipts / Shipments / Adjustments
   商品编辑页 stock 改只读 + 后端保存接口加校验
   本地 → 部署

6 production 菜单
   查一级 /stock 菜单 id
   UPDATE 旧"库存列表"改标题
   INSERT 5 新子菜单 + 角色分配
   登出重登 → 验证

7 全链路验证
   下单 → 出库单 + OUT流水 + stock扣减 + products.stock刷新 ✓
   售后 → 入库单 + IN流水 + stock回补 ✓
   手动入库 → 完成后 IN流水 + stock增加 ✓
   盘点调整 → 填调整后数量 → 自动算差异 ✓
   看板聚合正确 ✓
```

---

## 九、权限

```
1店铺管理员 / 2运营 → 全部
4仓储 → 看板(view) + SKU库存(list+edit) + 流水(view) + 单据(create+complete)
3客服 → 不给库存模块权限
```

---

## 十、前端渲染方案

### 10.1 技术栈与现有模式

```
Vue 3 <script setup> + Ant Design Vue + Vite + Vue Router
API 调用: @/api/xxx.js → axios 封装（baseURL /api，拦截器统一处理 token 和错误）
图表:     ECharts 按需引入，已有 BarChart.vue / LineChart.vue 封装
统计卡:   MiniStatisticCard.vue（复用工作台那套）
权限:     meta.permission + v-permission 指令，路由懒加载
```

### 10.2 目录结构

```
e-commerce/src/
  ├ api/
  │   ├ stock.js               ← 旧的 3 个函数废弃，全部重写
  │   └ products.js            ← 商品保存接口加校验（关手动改 stock）
  ├ pages/stock/
  │   ├ StockList.vue          ← 改旧文件，SKU 级（路径不变 /stock/stock-list）
  │   ├ Dashboard.vue          ← 新增 /stock/dashboard
  │   ├ Transactions.vue       ← 新增 /stock/transactions
  │   ├ Receipts.vue           ← 新增 /stock/receipts
  │   ├ Shipments.vue          ← 新增 /stock/shipments
  │   └ Adjustments.vue        ← 新增 /stock/adjustments
  ├ router/index.js            ← /stock children 加 5 条路由
  ├ utils/stock.js             ← 旧的 product 级 stockStatusOf 废弃，重写 SKU 级
  └ pages/goods/add-goods/AddGoods.vue + pages/goods/list/EditModal.vue
                               ← stock 输入框改只读 + 提示文字
```

### 10.3 Router 修改

```js
// router/index.js 里 /stock children 替换成：
{
  path: '/stock', name: 'Stock',
  meta: { isLogin: true, title: '库存管理', icon: 'InboxOutlined', renderMenu: true, cacheable: true },
  children: [
    { path: 'dashboard',    name: 'StockDashboard',    meta: { title: '库存看板',  permission: 'stock:dashboard:view' },          component: () => import('@/pages/stock/Dashboard.vue') },
    { path: 'stock-list',   name: 'SkuStockList',      meta: { title: 'SKU 库存',  permission: 'product:sku-stock:list' },         component: () => import('@/pages/stock/StockList.vue') },
    { path: 'transactions', name: 'StockTransactions', meta: { title: '库存流水',  permission: 'stock:transaction:view' },         component: () => import('@/pages/stock/Transactions.vue') },
    { path: 'receipts',    name: 'StockReceipts',     meta: { title: '入库单',    permission: 'stock:receipt:*' },                component: () => import('@/pages/stock/Receipts.vue') },
    { path: 'shipments',   name: 'StockShipments',    meta: { title: '出库单',    permission: 'stock:shipment:*' },               component: () => import('@/pages/stock/Shipments.vue') },
    { path: 'adjustments', name: 'StockAdjustments',  meta: { title: '调整单',    permission: 'stock:adjust:*' },                 component: () => import('@/pages/stock/Adjustments.vue') },
  ]
}
```

### 10.4 @/api/stock.js 重写（全部接口）

```js
import axios from './request.js'

// ===== SKU 库存 =====
export function getSkuStockList(params) {           // page, size, search, lowStockOnly
  return axios.get('/api/admin/stock/skus', { params })
}
export function setSkuAlert(skuId, data) {           // data: { minStock, maxStock }
  return axios.post(`/api/admin/stock/skus/${skuId}/alert`, data)
}

// ===== 看板 =====
export function getDashboard() {
  return axios.get('/api/admin/stock/dashboard')
}
export function getLowStockSku(limit = 20) {
  return axios.get('/api/admin/stock/dashboard/low-stock', { params: { limit } })
}
export function getTrend(days = 30) {
  return axios.get('/api/admin/stock/dashboard/trend', { params: { days } })
}

// ===== 流水 =====
export function getTransactions(params) {            // skuId, type, reason, startTime, endTime, orderItemId
  return axios.get('/api/admin/stock/transactions', { params })
}

// ===== 单据 =====
export function getStockOrders(params) {             // type, status, relatedNo, startTime, endTime
  return axios.get('/api/admin/stock/orders', { params })
}
export function getStockOrderDetail(id) {
  return axios.get(`/api/admin/stock/orders/${id}`)
}
export function createStockOrder(data) {             // type, bizType, items[], remark
  return axios.post('/api/admin/stock/orders', data)
}
export function completeStockOrder(id) {
  return axios.post(`/api/admin/stock/orders/${id}/complete`)
}
export function cancelStockOrder(id) {
  return axios.post(`/api/admin/stock/orders/${id}/cancel`)
}

// ===== SKU 搜索（建单时选 SKU 用）=====
export function searchSku(keyword) {                 // 前端调商品列表接口拿 SKU 展开
  return axios.get('/api/admin/products', { params: { search: keyword, page: 1, size: 20 } })
}
```

### 10.5 六个页面逐个说

#### 页面 1：StockList.vue（改旧文件 → SKU 级）

**核心变化**：从 product 级表格改成 SKU 级表格。一个商品展开成多行（每个 SKU 一行）。

```
接口切换：旧 getStockList → 新 getSkuStockList
搜索：SKU编码 / 商品名称（后端 product-service 用 LIKE 匹配 sku_code 和 products.name）
表格列：
  SKU编码 | 商品名称 | 规格组合(JSON → 渲染成 "曜石黑/128G") | 商品图 | 当前库存 | 安全阈值 | 库存状态Tag(售罄/低/正常/高) | 操作
库存状态：
  stock == 0                    → a-tag color=red   "售罄"
  0 < stock < min_stock         → a-tag color=orange "低库存"
  max_stock != null && stock > max_stock → a-tag color=blue  "高库存"
  其他                           → a-tag color=green  "正常"
操作列："设置阈值" → a-modal 弹窗，a-input-number 填 minStock / maxStock → setSkuAlert(skuId, data)
```

规格组合渲染：SKU 的 `spec_json` 是 `[{"name":"颜色","value":"曜石黑"},{"name":"容量","value":"128G"}]`，前端用一个 `formatSpec(specJson)` 函数把它变成 `"曜石黑/128G"` 字符串显示。

#### 页面 2：Dashboard.vue（新增）

**用现成组件**：MiniStatisticCard.vue + BarChart.vue（趋势图双柱对比 IN / OUT）。

```
布局：a-row :gutter="16" 分三段

第一段 a-col :span="6" × 4 统计卡（并排）：
  MiniStatisticCard title="SKU 总数"    :value="dashboard.skuTotal"    :icon="图标"
  MiniStatisticCard title="今日入库"    :value="dashboard.todayIn"     :icon="图标"
  MiniStatisticCard title="今日出库"    :value="dashboard.todayOut"    :icon="图标"
  MiniStatisticCard title="低库存 SKU"  :value="dashboard.lowStockSku" :icon="图标"
  → 数据：getDashboard()

第二段 a-card 低库存 TOP 20：
  a-table 直接写死 :columns="[商品名, SKU规格, 当前库存, 安全库存, 操作]"
  → 数据：getLowStockSku(20)
  → 操作列点"设阈值" → 复用 StockList.vue 里同款 a-modal（可以抽成 StockAlertModal.vue 组件，两处复用）

第三段 a-card 30 天趋势：
  用 BarChart.vue 但需要双 Series（IN柱 + OUT柱）
  → 现成 BarChart.vue 只支持单柱，改不了就自己在 Dashboard.vue 里直接用 echarts 原生 init + setOption 写一个双柱图
  → 数据：getTrend(30) 返回 [{ date: '2026-11-01', in: 120, out: 85 }, ...]
  → a-config-provider 不生效的暗色图表换成浅色（看板整体是白色背景）
```

#### 页面 3：Transactions.vue（新增）

**标准列表页**，和现有 OrderList.vue / AfterSaleList.vue 同风格。

```
顶部 a-card 过滤区：
  a-input-search placeholder="SKU名称/编码" → v-model 搜索词
  a-select placeholder="类型" 选项 [全部, IN入库, OUT出库, ADJUST调整]
  a-select placeholder="原因" 选项 [全部, 销售出库, 采购入库, 退货归还, 盘点调整, 手工补录]
  a-range-picker v-model 时间范围
  [查询] [重置]

中间 a-table 分页：
  SKU编码 | 商品名 | 规格 | 类型(Tag: IN绿/OUT橙/ADJUST蓝) | 数量 | 结存快照 | 原因 | 关联单号 | 操作人 | 时间
  → 关联单号列：如果 orderItemId 非空，值显示为可点击链接 → router.push(`/orders/order-list?highlight=${关联订单号}`)
  → 操作：点击行 → 展开详情或 a-modal 显示完整流水信息
底部 a-pagination :total="total" :current="page" :pageSize="size" @change="load"
```

#### 页面 4 & 5：Receipts.vue（入库单）+ Shipments.vue（出库单）

**两者结构几乎一样**，区别是 Receipts 有"新建单据"按钮，Shipments 主要看自动生成的。

```
顶部：Receipts 有 [+ 新建入库单] 按钮（Shipments 没有手动新建入口）
      a-card 过滤区：类型/状态/关联单号搜索/时间范围
中间 a-table：
  单据号 | 类型(Tag) | 原因 | 关联单号 | 明细行数 | 总数量 | 状态(Tag) | 操作人 | 时间 | 操作
  → 操作列：[查看详情] → a-modal 或路由跳详情页
Receipts type 列：RECEIPT 绿色 + 原因子标签
Shipments type 列：SHIPMENT 橙色 + 关联订单号可点击跳转
```

**新建入库单交互（ReceiptCreateModal.vue 组件）**：

```
a-modal 宽度 720px，确认按钮文字 "确认入库"

表单：
  a-form-item label="类型"：a-radio-group [采购入库 / 退货归还 / 手工补录]
  a-form-item label="备注"：a-textarea

明细行（a-table 内嵌，可增删）：
  每行 5 列：[SKU搜索下拉 / SKU编码 / 规格 / 数量(a-input-number) / 成本价(a-input-number) / 删除按钮]
  顶部 [+ 添加行] 按钮

SKU 搜索：a-select show-search 远程搜索，调用 searchSku(keyword)，选中后自动填 skuCode / specText
数量 必填，成本价可选

提交：createStockOrder({ type: 'RECEIPT', bizType, items: [...], remark })
      → 后端 product-service 直接创建 + complete（一个事务里做完）
      → 成功后 message.success + 关弹窗 + 刷新列表
```

#### 页面 6：Adjustments.vue（调整单，新增）

**和入库单结构一样，但明细行不同**：

```
明细行列：[SKU搜索 / SKU编码 / 规格 / 当前库存(只读显示) / 调整后数量 / 差异(自动算) / 删除]
  差异计算：after - current
  after > current → 差异显示绿色 + "+N" 标记（后端写 IN）
  after < current → 差异显示红色 + "-N" 标记（后端写 OUT）
  after == current → 差异灰 "0"（提交前校验差异不能为 0）

提交：createStockOrder({ type: 'ADJUST', items: [...], remark })
      → 后端逐行对比账实，算差异方向后调 StockWriteService.write()
```

### 10.6 关掉手动改 stock

**前端改**：
- AddGoods.vue（新增商品）+ EditModal.vue（编辑商品）里商品级 stock 输入框：
  - `<a-input-number v-model:value="form.stock" disabled />` + 下面一行灰色小字 "库存变动请通过库存模块入库单/调整单操作"
- SKU 级 SkuEditor.vue 里 costPrice（成本价）保留可编辑，但 stock 字段同理改 disabled

**后端改**（product-service 商品保存接口）：
- 保存商品时如果 DTO 里 stock 非空且和 DB 当前值不同，返回 `Result.fail("库存变更请通过库存模块操作")`
- 保存 SKU 时同理：stock 字段如果传了且变更了，拒绝

### 10.7 几个复用组件

| 组件 | 用在哪 | 说明 |
|---|---|---|
| StockAlertModal.vue（抽成独立组件） | SKU 库存页 + 看板低库存 TOP20 | 输入 minStock / maxStock，调 setSkuAlert |
| OrderItemsTable.vue（抽成独立组件） | 入库单 / 调整单 新建弹窗 | 可增删的 SKU 明细行，SKU 搜索下拉 |
| OrderNoTag.vue（如果需要） | 所有单据列表页 | 美化 RK/CK/PD + 日期 + 序号 的单据号显示 |

### 10.8 开发顺序建议

```
1 api/stock.js 重写（所有接口函数先定义好，哪怕后端还没实现）
2 utils/stock.js 重写（formatSpec / skuStatusOf）
3 StockList.vue 改造（改旧文件，SKU 级，最基础的一页）
4 router/index.js 路由追加（前端单独跑起来，菜单用前端写死或等 production INSERT）
5 Dashboard.vue（看板，复用 MiniStatisticCard + BarChart）
6 Transactions.vue（流水，标准列表页）
7 Receipts.vue + ReceiptCreateModal.vue（入库单）
8 Shipments.vue（出库单，结构类似 Receipts 但更简单）
9 Adjustments.vue（调整单，明细行逻辑稍复杂）
10 关掉手动改 stock（AddGoods.vue + EditModal.vue + 后端校验）
```
