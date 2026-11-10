# 库存看板 + 批量预警 改版方案

> 参考高道云 WMS 库存看板 / 仓库流水看板视觉布局，适配本项目现有数据模型（无 warehouse 多仓概念、SKU 830 条、stock_transaction 仅 IN/OUT/ADJUST 三种类型、演示场景）。

---

## 一、当前问题

### 1.1 信息密度低
现有 4 张统计卡只覆盖：SKU 总数 / 今日入库 / 今日出库 / 低库存预警。
**缺失**：总库存件数、库存总价值、在售商品 SKU 占比。

### 1.2 图表单一
只有 30 天出入库柱状图一种。**缺失**：业务类型占比（IN/OUT/ADJUST 饼图）、各 SKU 库存分布柱状图。

### 1.3 视觉朴素
- 统计卡只有 icon 小图标，没有渐变背景、数字强调、环比对比
- 柱状图配色绿/橙对比度低，缺少 ECharts 面积填充
- 低库存表格纯文本，没有风险级别高亮（紧急/一般/关注）

### 1.4 低库存表格字段少
现有只展示：商品名 / SKU 规格 / 当前库存 / 安全下限 / 设阈值。
参考图有：仓库、产品名称、规格型号、入库数量、出库数量、当前库存、出库频率、可用库存数量。

### 1.5 无时间范围切换
参考图有"今天 / 昨天 / 本周 / 本月 / 今年"快捷切换。当前固定 30 天。

---

## 二、改版方案

### 2.1 页面布局（自上而下）

```
┌─────────────────────────────────────────────────────────────┐
│  筛选区：时间范围 [本周 ▼]   [刷新]                         │
├──────────┬──────────┬──────────┬───────────────────────────┤
│ 总库存件  │ SKU 总数  │ 今日入库  │ 今日出库                    │
│  64,258  │   830    │    110   │     1                      │
├──────────┴──────────┴──────────┴───────────────────────────┤
│                                                             │
│  ┌─── 30 天出入库趋势（折线图 + 面积填充）─────────────┐   │
│  │  █──────────────────────────────────────────────█  │   │
│  │  █   ▲                    ╱╲                    █  │   │
│  │  █   │        ╱╲        ╱  ╲      ╱╲            █  │   │
│  │  █───┴───────╱──╲──────╱────╲────╱──╲──────────█   │   │
│  └────────────────────────────────────────────────────┘   │
│                                                             │
│  ┌─── 业务类型占比 ─────┐  ┌─── SKU 库存分布 TOP 15 ───┐  │
│  │  ╭─────╮             │  │  ▌  800+ ████████████████ │  │
│  │  │ IN  │ 45%         │  │  ▌  600  ██████████       │  │
│  │  │ OUT │ 40%         │  │  ▌  400  ██████           │  │
│  │  │ ADJ │ 15%         │  │  ▌  200  ███              │  │
│  │  ╰─────╯             │  │  ▌   50  █                │  │
│  └───────────────────────┘  └───────────────────────────┘  │
│                                                             │
│  ┌─── 低库存预警 SKU ──────────────────────────────────┐  │
│  │ 级别 │ 商品名 │ SKU 规格 │ 当前 │ 下限 │ 今日出库 │ │  │
│  │ 🔴紧急│ xxx   │ 黑色/64G │  5   │  20  │   3     │ │  │
│  │ 🟡一般│ xxx   │ 白色/128G│ 18   │  30  │   1     │ │  │
│  └──────────────────────────────────────────────────────┘  │
│                                                             │
└─────────────────────────────────────────────────────────────┘
```

### 2.2 区块详细设计

#### 区块 1：筛选区
- **时间范围**：今天 / 7 天 / 30 天 / 90 天（快捷 Tab，而非下拉）
- **刷新按钮**：右上角，点击重新拉全部数据

#### 区块 2：统计卡（4 张，调整内容 + 视觉）

| 卡片 | 数字来源 | 视觉 |
|------|---------|------|
| 总库存件数 | `SUM(stock)` FROM product_sku WHERE status=1 | 渐变蓝背景 / 白色大号数字 |
| SKU 总数 | `COUNT(*)` FROM product_sku WHERE status=1 | 渐变绿背景 |
| 今日入库 | `SUM(qty)` FROM stock_transaction WHERE type='IN' AND DATE(create_time)=today | 渐变橙背景 / +今日 小字 |
| 今日出库 | `SUM(qty)` FROM stock_transaction WHERE type='OUT' AND DATE(create_time)=today | 渐变红背景 / +今日 小字 |

> 参考图用的是纯色白卡加淡色 icon，本项目 MiniStatisticCard 可以改 CSS 实现渐变背景。

#### 区块 3：30 天出入库趋势图（折线 + 面积）
- **改**：从柱状图 → 折线图 + 渐变面积填充
- **理由**：参考高道云两张图都是折线 + 面积，更有"走势感"；柱状图是离散对比，折线是连续趋势
- **配色**：入库 绿色渐变 / 出库 橙色渐变，面积透明度 0.3
- **交互**：hover 显示 tooltip（日期 / 入库数量 / 出库数量）

#### 区块 4：业务类型占比饼图（新增）
- **数据**：`SELECT type, COUNT(*), SUM(qty) FROM stock_transaction WHERE date BETWEEN ? GROUP BY type`
- **类型**：IN（入库）/ OUT（出库）/ ADJUST（盘点调整）
- **配色**：IN=#52c41a 绿 / OUT=#fa8c16 橙 / ADJUST=#1890ff 蓝
- **备注**：数据量不大（演示场景），饼图足够

#### 区块 5：SKU 库存分布柱状图（水平，新增）
- **数据**：`SELECT p.name as productName, s.spec_text, s.stock FROM product_sku s JOIN products p ON p.id=s.product_id WHERE s.status=1 ORDER BY s.stock DESC LIMIT 15`
- **理由**：一眼看出哪些 SKU 占库存大头（参考图里 800 件的红色大柱子很醒目）
- **配色**：根据库存分级（>500 红 / 200-500 橙 / <200 灰）
- **布局**：左侧商品名 + 规格，右侧水平柱状图 + 数字

#### 区块 6：低库存预警 SKU 表（改字段 + 加颜色）
- **改**：字段从 5 列 → 7 列，新增"风险级别"和"今日出库"
- **风险级别逻辑**（后端 Mapper SQL `CASE` 计算为 `riskLevel` 字段）：
  - 🔴 紧急 `urgent`：`stock = 0`（断货）
  - 🟠 高 `high`：`stock < min_stock * 0.3`
  - 🟡 中 `mid`：`stock < min_stock`
- **新增字段**：
  - `riskLabel`：后端 Java 层把 riskLevel 映射成中文（紧急/高/中）
  - `stockRate`：当前库存 / 安全下限（0.25 表示只有下限的 25%）
  - `todayOutQty`：今日该 SKU 出库数量（辅助判断是否正在持续缺货）

> 当前所有在售 SKU 已通过 `UPDATE product_sku SET min_stock=50, max_stock=500 WHERE status=1` 批量设了预警阈值（本地 + 生产均已执行）。低库存表数据会在 stock < 50 时开始产生。

### 2.3 后端接口变更

| 接口 | 操作 | 内容 |
|------|------|------|
| `GET /api/admin/stock/dashboard` | **改** | 新增 `totalStock`（总库存件数），原有 `skuTotal` 保留（在售 SKU 数）、`lowStockSku` 保留 |
| `GET /api/admin/stock/dashboard/trend` | **保留** | 折线图继续用，`days` 参数支持 7/30/90 |
| `GET /api/admin/stock/dashboard/type-dist` | **新增** | 业务类型占比：`[{type, count, qty, label}]` |
| `GET /api/admin/stock/dashboard/sku-dist` | **新增** | SKU 库存分布 TOP N：`[{productName, specText, stock}]` |
| `GET /api/admin/stock/dashboard/low-stock` | **改** | 新增 `riskLevel`、`riskLabel`、`stockRate`、`todayOutQty` |
| `POST /api/admin/stock/skus/alert/batch` | **新增** | 批量设 SKU 预警，Body `{skuIds:[], minStock, maxStock}`，权限 `product:sku-stock:list` |

> 所有后端接口权限码：`stock:dashboard:view`（看板）、`product:sku-stock:list`（SKU 列表 + 预警）、`stock:*:*`（流水/单据）。admin_menu 种子数据已存在（id=922 看板、id=400 SKU 库存）。

### 2.4 实现优先级

| 阶段 | 内容 | 预估工时 |
|------|------|---------|
| **P0（必做）** | 统计卡改版（加 totalStock、改渐变配色）+ 折线面积图替换柱状图 + 低库存表加风险级别 | 2h |
| **P1（推荐）** | 业务类型占比饼图 + SKU 库存分布水平柱状图 | 3h |
| **P2（可选）** | 时间范围快捷切换 Tab（今天/7天/30天/90天） | 0.5h |

### 2.5 不在本次范围内（保持简单）

- ❌ 多仓（warehouse）概念：项目无 warehouse 表，所有库存集中管理
- ❌ 周转率 / 出库频率：需要 30 天以上历史流水才能算，演示场景流水太少
- ❌ 业务单据类型细分（采购入库 / 调拨入库 / 退货入库）：stock_transaction.type 只有 3 种
- ❌ 图表筛选联动（点饼图某类 → 折线图只显示该类数据）：演示场景不需要这么复杂的交互

---

## 三、参考图对比

| 参考图（高道云 WMS） | 本项目 | 差距 |
|----------------------|--------|------|
| 4 张统计卡（当前库存/可用库存/活动数/产品可用库存） | 4 张统计卡（SKU 总数/今日入/今日出/低库存数） | 参考图把"库存"卡放第一位，本项目改后也把"总库存件数"放第一 |
| 产品可用库存柱状图（水平，800+ 那根红柱最显眼） | 无 | 这是参考图最有信息量的图表，P1 加 |
| 仓库流水看板的出入库趋势折线图（面积填充） | 出入库柱状图 | 折线+面积比柱状图更能表达走势 |
| 出入库类型数据饼图 | 无 | P1 加 |
| 库存周转率表（8 列，含仓库/出入库频率） | 低库存表（5 列） | 简化：去掉周转率（没历史数据），加风险级别 + 今日出库 |
| 时间 Tab（今天/昨天/本周/本月/今年） | 固定 30 天 | P2 加 |

---

## 四、接口返回结构预览

### 4.1 看板聚合 `GET /api/admin/stock/dashboard`

```json
{
  "skuTotal": 830,
  "totalStock": 64258,
  "todayIn": 110,
  "todayOut": 1,
  "lowStockSku": 0
}
```

### 4.2 业务类型占比 `GET /api/admin/stock/dashboard/type-dist?days=30`

```json
[
  { "type": "IN",     "count": 12, "qty": 580, "label": "入库" },
  { "type": "OUT",    "count": 8,  "qty": 340, "label": "出库" },
  { "type": "ADJUST", "count": 3,  "qty": 45,  "label": "盘点调整" }
]
```

### 4.3 SKU 库存分布 `GET /api/admin/stock/dashboard/sku-dist?limit=15`

```json
[
  { "productName": "小米手机14", "specText": "黑色/12+256", "stock": 856 },
  { "productName": "iPhone 15", "specText": "蓝色/128G",    "stock": 623 }
]
```

### 4.4 低库存（改）

```json
[
  {
    "skuId": 101,
    "productName": "AirPods 3",
    "specText": "白色/标准版",
    "stock": 5,
    "minStock": 50,
    "riskLevel": "urgent",
    "riskLabel": "紧急",
    "stockRate": 0.10,
    "todayOutQty": 3
  }
]
```

---

## 五、SQL 草案（后端 Mapper 实现参考）

```sql
-- 1. 总库存 + 在售 SKU 数
SELECT SUM(stock) AS totalStock, COUNT(*) AS productSkuCount
FROM product_sku WHERE status = 1;

-- 2. 业务类型占比（近 N 天）
SELECT type, COUNT(*) AS count, SUM(qty) AS qty
FROM stock_transaction
WHERE create_time >= NOW() - INTERVAL 30 DAY
GROUP BY type;

-- 3. SKU 库存分布 TOP 15
SELECT p.name AS productName, s.spec_text AS specText, s.stock AS stock
FROM product_sku s
JOIN products p ON p.id = s.product_id
WHERE s.status = 1
ORDER BY s.stock DESC
LIMIT 15;

-- 4. 低库存 SKU + 今日出库量 + 风险级别
SELECT
  s.id AS skuId,
  p.name AS productName,
  s.spec_text AS specText,
  s.stock AS stock,
  s.min_stock AS minStock,
  CASE
    WHEN s.stock = 0 THEN 'urgent'
    WHEN s.stock < s.min_stock * 0.3 THEN 'high'
    ELSE 'mid'
  END AS riskLevel,
  ROUND(s.stock / NULLIF(s.min_stock, 0), 2) AS stockRate,
  COALESCE(t.todayOut, 0) AS todayOutQty
FROM product_sku s
JOIN products p ON p.id = s.product_id
LEFT JOIN (
  SELECT sku_id, SUM(qty) AS todayOut
  FROM stock_transaction
  WHERE type = 'OUT' AND DATE(create_time) = CURDATE()
  GROUP BY sku_id
) t ON t.sku_id = s.id
WHERE s.status = 1
  AND s.min_stock IS NOT NULL
  AND s.stock < s.min_stock
ORDER BY s.stock ASC
LIMIT 20;
```

---

## 六、组件依赖

| 组件 | 用途 | 现状 |
|------|------|------|
| `MiniStatisticCard` | 统计卡 | 已有，改 CSS 渐变背景即可 |
| ECharts | 折线面积图 / 饼图 / 水平柱状图 | 已有（当前只用了 BarChart，需要加 LineChart / PieChart） |
| `a-tabs` | 时间范围快捷切换 | ant-design-vue 内置 |

无需引入新组件库。

---

## 七、批量设置 SKU 预警（新增功能）

### 7.1 当前问题

项目有 830 条 SKU，每条"设阈值"按钮要单独打开弹窗保存一次，830 个 SKU 设预警要点 830 次。**没有批量设置能力。**

| 现有 | 缺 |
|------|-----|
| 单条设置接口 `POST /api/admin/stock/sku/{skuId}/alert` | 批量设置接口 |
| StockList.vue 每行一个"设阈值"按钮 | 表格 rowSelection 多选 + 批量按钮 |
| 无 | 批量弹窗（表单复用单条弹窗逻辑） |

### 7.2 方案

#### 后端

**Mapper 加 batchUpdateAlert：**

```xml
<!-- StockSkuMapper.xml 风格 -->
<update id="batchUpdateAlert">
    UPDATE product_sku
    SET min_stock = #{minStock},
        max_stock = #{maxStock},
        update_time = NOW()
    WHERE id IN
    <foreach collection="skuIds" item="id" open="(" separator="," close=")">#{id}</foreach>
</update>
```

**Controller 加批量接口（已实现）：**

```
POST /api/admin/stock/skus/alert/batch    ← 注意是复数 skus（Controller @RequestMapping=/skus）
Body: { "skuIds": [101, 102, 103], "minStock": 20, "maxStock": null }
权限：product:sku-stock:list（跟单条设阈值共用）
返回：成功条数
```

> minStock / maxStock 为 null 表示清除对应阈值（与单条接口语义一致）。

#### 前端 StockList.vue（已实现）

1. **表格 `rowSelection`** — 左边出多选框，`preserveSelectedRowKeys: true` 翻页保留选中
2. **批量按钮** — 过滤条右侧，`批量设阈值（已选 N 条）`，未选中时禁用；已加 `v-permission="'product:sku-stock:list'"`
3. **批量弹窗** — 复用单条弹窗的表单，标题改"批量设置 SKU 预警阈值"
4. **单条按钮** — 设阈值也已加 `v-permission="'product:sku-stock:list'"`

### 7.3 工作量

| 层 | 改动 | 工时 |
|----|------|------|
| StockSkuMapper.xml | 加 batchUpdateAlert SQL | 5min |
| StockSkuMapper.java | 加方法签名 | 2min |
| StockSkuController | 加 batch 接口 | 10min |
| StockList.vue | rowSelection + 批量按钮 + 批量弹窗 | 40min |
| **合计** | | **~1h** |

### 7.4 不在范围内

- ❌ 按商品分类批量（"手机类全部设下限 20"）：演示场景品类少，勾选 SKU 够快
- ❌ 按比例规则（"下限 = 当前库存 × 10%"）：需要先算每条比例值
- ❌ Excel 模板导入：已有库存 Excel 模板，再加一套冗余

---

## 八、当前实现状态（2026-11-09）

### ✅ 已完成：看板改版

| 层 | 文件 | 状态 |
|----|------|------|
| Mapper XML | StockSkuMapper.xml（batchUpdateAlert / lowStockDetail / stockDistTopN） | ✅ |
| Mapper Java | StockSkuMapper.java | ✅ |
| Mapper XML | StockTransactionMapper.xml（typeDist / totalStock） | ✅ |
| Mapper Java | StockTransactionMapper.java | ✅ |
| Controller | StockDashboardController.java（5 个接口全改） | ✅ |
| Controller | StockSkuController.java（单条 + 批量预警） | ✅ |
| 前端 API | stock.js（6 个函数） | ✅ |
| 前端看板 | Dashboard.vue（渐变卡 + 折线面积 + 饼图 + 水平柱 + 风险标签） | ✅ |
| 前端 SKU 列表 | StockList.vue（多选 + 批量弹窗 + v-permission 权限指令） | ✅ |

### ✅ 已完成：数据修复

| 操作 | SQL | 本地 | 生产 |
|------|-----|------|------|
| 批量设 SKU 预警 | `UPDATE product_sku SET min_stock=50, max_stock=500 WHERE status=1` | ✅ 830 条 | ✅ 800 条 |
| products.body 清文字留图 | Python 脚本批量 `<p>` 剥离 | ✅ 136 条 | ✅ 136 条 |
| promotion_banner 物理删除 | 改 BannerMapper.softDelete → deleteById（`DELETE FROM promotion_banner WHERE id=?`） | ✅ 代码改完 | 重新部署 product-service 生效 |

### ✅ 已清理：beautifyBody 简化

products.body 已清成只剩 `<img>`，H5 `beautifyBody()` 和小程序 `bodyHtml` computed 里「剥参数区」「删不含图段落」的正则永远匹配不到任何东西，已简化为只做懒加载 + 全宽显示。同时清理了 H5 + 小程序里为 `<p>` 卖点大标题写的 CSS。

### ⚠️ 需要重新部署

改动都在 product-service（后端）和前端电商后台、商城 H5、小程序。上线时：

1. **product-service** `mvn clean package` → 重新部署容器（Banner 物理删除 + 看板新接口生效）
2. **前端电商后台** `vite build` → OSS 上传
3. **商城 H5** `vite build` → OSS 上传
4. **小程序** `npm run build:mp-weixin` → 上传微信
5. **SQL** 本次执行的 SQL 都已在本地 + 生产两端同步（批量预警阈值、body 清理），没有遗漏
