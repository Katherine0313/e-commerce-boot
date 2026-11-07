# 杰米商城 · 移动端适配方案

> 目标：`ecommerce-mall`（Vue3 + Element Plus + Vite）在手机屏幕下可用、可下单。
> 现状：PC 优先设计，仅零散加了几个断点，移动端体验不完整。

---

## 1. 现状盘点

### 1.1 已有的响应式基础

| 位置 | 已有断点 | 做了什么 |
|---|---|---|
| [style/index.css](../../ecommerce-mall/style/index.css) | 768px | `.container` 收窄、`product-grid` 变 2 列 |
| [DefaultLayout.vue](../../ecommerce-mall/src/views/layouts/DefaultLayout.vue) | 640px | 顶部工具条文字入口隐藏、头部栅格改 flex 换行 |
| [Home.vue](../../ecommerce-mall/src/views/Home.vue) | 1200/992/768/560px | 猜你喜欢逐级降列（5→4→3→2） |
| [ProductDetail.vue](../../ecommerce-mall/src/views/products/ProductDetail.vue) | 1024px | 主图/信息栏上下堆叠 |
| [Cart.vue](../../ecommerce-mall/src/views/products/Cart.vue) | 1000/767px | 购物车两栏改单栏、卡片换行 |
| [UserLayout.vue](../../ecommerce-mall/src/views/layouts/UserLayout.vue) | 900px | 左侧菜单改横向堆叠 |
| [Settlement.vue](../../ecommerce-mall/src/views/products/Settlement.vue)、[Paid.vue](../../ecommerce-mall/src/views/products/Paid.vue) | 1200/767px | 结算/支付页单栏 |
| [Profile.vue](../../ecommerce-mall/src/views/user/Profile.vue) | 900px | 功能宫格 3 列 |
| [Login.vue](../../ecommerce-mall/src/views/Login.vue)、[ApplyAfterSale.vue](../../ecommerce-mall/src/views/aftersales/ApplyAfterSale.vue)、[AfterSaleDetail.vue](../../ecommerce-mall/src/views/aftersales/AfterSaleDetail.vue) | 480/640px | 表单窄屏微调 |

**结论**：断点很碎（480/560/640/767/768/900/992/1000/1024/1200 十档），且各页面各自为政。

### 1.2 明确的移动端问题

| # | 问题 | 位置 | 症状 |
|---|---|---|---|
| P1 | 缺少 `<meta viewport>` 的 `viewport-fit` | [index.html](../../ecommerce-mall/index.html) | iPhone 刘海屏 / 底部横条区域不避让 |
| P2 | 弹窗/抽屉固定像素宽，超出手机屏 | `AddCartDialog.vue`(360px)、`OrderDetail.vue` 物流(480px)、`Place.vue`/`Address.vue` 抽屉(480px) | 480px 抽屉在 375px 屏上溢出 |
| P3 | 表单 `label-width` 固定 110px/90px | `ApplyAfterSale.vue`、`AfterSaleDetail.vue`、`Address.vue` | 窄屏标签占位过宽，输入框被挤窄 |
| P4 | 结算页 `width: 700px` 硬编码 | [Settlement.vue](../../ecommerce-mall/src/views/products/Settlement.vue):368 | 375px 屏横向溢出 |
| P5 | 商品详情主图区 `width: 660px` 硬编码 | [ProductDetail.vue](../../ecommerce-mall/src/views/products/ProductDetail.vue):746 | 同上 |
| P6 | 搜索页无独立窄屏样式 | [Search.vue](../../ecommerce-mall/src/views/Search.vue) | 依赖全局 2 列栅格，筛选区挤压 |
| P7 | 订单列表 / 收藏 / 优惠券 / 会员 无窄屏处理 | `OrderList.vue`、`Favorites.vue`、`Coupons.vue`、`Member.vue` | 表格/横排布局挤压错乱 |
| P8 | 无底部安全区适配 | 全站 | iPhone 底部横条遮挡按钮 |
| P9 | 顶部导航无汉堡菜单 | `DefaultLayout.vue` | 窄屏下「我的订单/收藏/优惠券/会员」入口全部消失，只剩购物车+头像 |
| P10 | 触控目标偏小 | 通用 | 部分可点元素 < 44px |

---

## 2. 适配策略

### 2.1 断点收敛为 3 档

现状 10 档断点 → 收敛成 3 档，和主流做法对齐：

| 档位 | 断点 | 布局形态 | 覆盖设备 |
|---|---|---|---|
| **mobile** | `≤ 768px` | 单栏为主，底部 Tab 导航 | 手机竖屏 |
| **tablet** | `769px ~ 1024px` | 双栏但压缩间距 | iPad 竖屏 / 小笔记本 |
| **desktop** | `> 1024px` | 保持现状 | 桌面 |

规则：**新增媒体查询只用这三档**，不再新增零散断点。

#### 现有断点并入对照

| 现有断点 | 出现在 | 并入档位 | 处理方式 |
|---|---|---|---|
| `480px` | `Login.vue`、`AfterSaleDetail.vue` | mobile | 保留在 mobile 档内部（`@media (max-width:768px)` 里再嵌套），不改语义 |
| `560px` | `Home.vue` 猜你喜欢降列 | mobile | 同上，作为 mobile 档内的更细一档 |
| `640px` | `DefaultLayout.vue`、`ApplyAfterSale.vue` | mobile | 同上 |
| `767px` | `Settlement.vue`、`Cart.vue`、`Place.vue`、`Paid.vue` | mobile | **改为 768px**（见下方边界问题） |
| `768px` | `style/index.css`、`Home.vue` | mobile | 作为 mobile 档基准，保留 |
| `900px` | `UserLayout.vue`、`Profile.vue` | tablet | 改为 `1024px` 归入 tablet，或保留在 tablet 档内 |
| `992px` | `Home.vue` 猜你喜欢降列 | tablet | 保留在 tablet 档内 |
| `1000px` | `Cart.vue` 购物车两栏折行 | tablet | 归入 tablet 档（`1024px`） |
| `1024px` | `ProductDetail.vue` 主图堆叠 | tablet | 作为 tablet 档基准，保留 |
| `1200px` | `Home.vue`、`Settlement.vue`、`Paid.vue` | desktop 细分 | **保留**，作为 desktop 档内部的宽度优化（不违反三档原则） |

#### 必须修掉的边界断层（现存 bug）

现在 `767px` 和 `768px` **混用**：

- `style/index.css`、`Home.vue` 用 `max-width: 768px`
- `Settlement.vue`、`Cart.vue`、`Place.vue`、`Paid.vue` 用 `max-width: 767px`

导致视口**恰好等于 768px** 时，两套规则都不生效——夹在两档之间的 1px 空隙，布局会出现错乱（如结算页突然变回两栏）。

**统一为 768px**，不留空隙。

#### 三档的语义边界（避免「什么都能塞进 768px」）

| 档位 | 判定依据 | 该档必须做的事 |
|---|---|---|
| **mobile** | 视口 ≤ 768px | ① 所有多列改单列或横向滚动<br>② 出现底部 Tab 导航<br>③ 可点元素 ≥ 44px<br>④ 主操作按钮固定底部 |
| **tablet** | 视口 769–1024px | ① 保留双栏但压缩间距<br>② 不出现底部 Tab<br>③ 弹窗宽度按比例而非固定 px |
| **desktop** | 视口 > 1024px | 保持现有 PC 布局不变 |

**判定用视口宽度，不用设备类型**（不靠 UA 判断）。理由：UA 分不出 iPhone SE(375) 和 Pro Max(430)，也处理不了折叠屏展开和iPad 桌面模式。

### 2.2 三条铁律

1. **不写死像素宽度**——所有 `width: XXXpx` 改成 `width: 100%` + `max-width`
2. **不隐藏核心功能**——窄屏可以换形态，不能消失（顶部导航要变汉堡菜单，不能直接藏掉）
3. **可点区域 ≥ 44px**——移动端最小触控目标

---

## 3. 全局基础设施（P0）

### 3.1 viewport 与安全区

[index.html](../../ecommerce-mall/index.html)：

```html
<meta name="viewport" content="width=device-width, initial-scale=1.0, viewport-fit=cover" />
```

[style/index.css](../../ecommerce-mall/style/index.css) 加安全区变量：

```css
:root {
  --safe-top: env(safe-area-inset-top, 0px);
  --safe-bottom: env(safe-area-inset-bottom, 0px);
  --safe-left: env(safe-area-inset-left, 0px);
  --safe-right: env(safe-area-inset-right, 0px);
  --tabbar-height: 56px;   /* 移动端底部导航高度 */
}
```

用法：固定底栏 `padding-bottom: calc(var(--space-md) + var(--safe-bottom))`，左右通栏元素 `padding-left: var(--safe-left)`。

### 3.2 全局窄屏基线

[style/index.css](../../ecommerce-mall/style/index.css) 末尾加一档统一兜底（只覆盖公共类，不逐页写）：

```css
@media (max-width: 768px) {
  .container {
    width: calc(100% - 24px);
  }

  .section-title {
    font-size: 18px;
    margin: var(--space-md) 0 var(--space-sm);
  }

  /* 通用：横向滚动容器代替挤压 */
  .h-scroll {
    display: flex;
    gap: var(--space-sm);
    overflow-x: auto;
    scroll-snap-type: x mandatory;
    -webkit-overflow-scrolling: touch;
  }
  .h-scroll > * {
    flex: 0 0 auto;
    scroll-snap-align: start;
  }
}
```

### 3.3 弹窗/抽屉全屏化

Element Plus 的 `el-dialog` / `el-drawer` 在窄屏统一处理：

```css
@media (max-width: 768px) {
  .el-dialog {
    width: calc(100% - 24px) !important;
    margin-top: 12vh !important;
  }

  /* 抽屉改全屏从底部升起 */
  .el-drawer.rtl {
    width: 100% !important;
  }
}
```

对应需要改的固定宽度：
- `AddCartDialog.vue` `width="360px"` → `width="92%"` + `max-width: 420px`
- `OrderDetail.vue` 物流 `width="480px"` → `width="92%"`
- `Place.vue` / `Address.vue` `size="480px"` → 窄屏由 CSS 强制 `width: 100%`

### 3.4 表单窄屏规则

```css
@media (max-width: 768px) {
  .el-form-item {
    display: block;      /* 标签与内容换行，不再并排 */
  }
  .el-form-item__label {
    width: auto !important;
    text-align: left;
    padding-bottom: 4px;
    line-height: 1.4;
  }
  .el-form-item__content {
    margin-left: 0 !important;
  }
}
```

解决 P3（`label-width="110px"` / `90px` 在窄屏占位过宽）。

---

## 4. 底部 Tab 导航（P0，移动端核心）

**动机**：顶部导航在窄屏被砍掉后，「我的订单/收藏/优惠券/会员」全部消失。改用底部 Tab 是电商移动端标准做法。

### 4.1 新增组件

`ecommerce-mall/src/components/MobileTabBar.vue`：

| Tab | 路由 | 图标 |
|---|---|---|
| 首页 | `/` | HomeFilled |
| 分类 | `/products-list` | Grid（Element Plus 没有，用 `Menu` 或自定义） |
| 购物车 | `/cart` | ShoppingCart（带数量角标） |
| 我的 | `/user` | User |

### 4.2 挂载位置

在 `DefaultLayout.vue` 里 `main` 之后、`footer` 之前：

```vue
<MobileTabBar v-if="!route.meta.hideTabBar" />
```

### 4.3 样式与行为

```css
.mobile-tabbar {
  position: fixed;
  left: 0;
  right: 0;
  bottom: 0;
  z-index: var(--z-header);
  display: none;
  height: var(--tabbar-height);
  padding-bottom: var(--safe-bottom);
  background: #fff;
  border-top: 1px solid var(--border-light);
}

@media (max-width: 768px) {
  .mobile-tabbar { display: flex; }
}
```

- 底部有 tabbar 时给 `main` 加 `padding-bottom: calc(var(--tabbar-height) + var(--safe-bottom))`
- `route.meta.hideHeader` 的页面（商品详情/购物车/结算/支付）**不显示 tabbar**，聚焦下单流程
- 购物车角标取 `cartStore` 的数量

### 4.4 顶部导航窄屏改汉堡菜单

`DefaultLayout.vue` 640px 断点里，保留「我的订单/收藏/优惠券/会员」的入口但折叠成抽屉：

- 640px 以下：顶部只留 logo + 搜索框 + 购物车图标 + 汉堡按钮
- 汉堡点击展开 `el-drawer`（从左），内含：首页 / 我的订单 / 我的收藏 / 我的优惠券 / 会员中心 / 个人中心 / 退出登录

---

## 5. 逐页适配清单（P0）

### 5.1 首页 `Home.vue`

| 项 | 现状 | 改法 |
|---|---|---|
| 轮播图 | 固定高度 | 移动端 `height: 180px`，`el-carousel` 自适应 |
| 分类宫格 | 固定列数 | 移动端 4 列 → 3 列（每格图标缩小） |
| 商品网格 | 2 列 | 保持 2 列，图片比例 `aspect-ratio: 1` 固定避免抖动 |
| 「猜你喜欢」 | 已有 560px→2 列 | 保持 |

### 5.2 商品列表 `ProductList.vue` / 搜索 `Search.vue`

列表页本身用的是全局 `.product-grid`（768px 已 2 列），**基本 OK**。需补：

- 搜索页顶部筛选区：窄屏改为**可横向滚动的 chip 栏**（用 3.2 的 `.h-scroll`）
- 排序/筛选下拉：窄屏改用底部抽屉触发，不占顶部空间
- 空状态 `el-empty` 尺寸缩小

### 5.3 商品详情 `ProductDetail.vue`（重点）

| 断点 | 改法 |
|---|---|
| 基础 | 去掉 `width: 660px` / `width: 700px` 硬编码，改 `width: 100%` |
| 1024px | 已有堆叠，保留 |
| 768px | 主图 `max-height: 320px`；SKU 选择区从多列改单列；价格区字号缩小 |
| 480px | 底部「加入购物车 / 立即购买」按钮**固定在底部**，滚动时始终可见；加 `padding-bottom` 避让安全区 |

底部固定购买栏是移动端电商转化率的关键，不能让按钮滚出屏幕。

### 5.4 购物车 `Cart.vue`

已有 767px 单栏处理，补充：

- 移动端**隐藏表头**（已有 `display: none`）
- 商品图缩到 72px
- 「全选 / 删除 / 结算」操作栏固定底部
- 空购物车时给「去逛逛」按钮跳首页

### 5.5 结算 `Settlement.vue`（重点）

- 去掉 `width: 700px`，改 `width: 100%`
- 收货地址区：移动端**单列展示**，不并排
- 商品清单：横向滚动缩略图（`.h-scroll`），不逐行占全宽
- 金额明细：`font-size: 13px`
- 底部「提交订单」按钮固定底部

### 5.6 支付结果 `Paid.vue`

- 单栏已有，补充固定底部「查看订单 / 继续逛逛」按钮组

### 5.7 个人中心 `UserLayout.vue` + `Profile.vue`

| 项 | 改法 |
|---|---|
| 左侧菜单 | 900px 已改堆叠；**移动端改成横向滚动的 chip 栏**，而不是纵向堆叠（纵向会占掉一屏） |
| 用户名片 | 头像 64→48，`user-right` 三个统计数字改一行紧凑排列 |
| 功能宫格 | 已有 900px→3 列；移动端保持 3 列（6 个正好两行），或 4 列更密 |
| 订单状态入口 | 5 个状态改横向滚动（`.h-scroll`），避免换行占高 |

### 5.8 订单相关 `OrderList.vue` / `OrderDetail.vue`

**当前完全没有窄屏样式**，需要新写：

`OrderList.vue`：
- 订单卡片：商品图 + 名称（省略号）+ 价格 + 数量
- 状态标签右上角
- 底部操作按钮组（待付款 → 取消/去支付）

`OrderDetail.vue`：
- 物流轨迹弹窗 `width: 480px` → `92%`
- 状态时间轴：竖向排列，节点间距缩小
- 商品清单同结算页处理

### 5.9 收藏 / 优惠券 / 会员

三个页面都用横向排列卡片，窄屏改：
- 收藏：网格 2 列
- 优惠券：单列卡片（券面横向改为竖排）
- 会员：进度条和权益列表单列

### 5.10 售后 `ApplyAfterSale.vue` / `AfterSaleDetail.vue`

- 表单用 3.4 的全局窄屏规则
- 售后原因选择：横向滚动 chip

### 5.11 登录 `Login.vue`

已有 480px 处理，补充：
- 第三方登录图标间距窄屏缩小
- 键盘弹起时不要遮挡输入框（`visualViewport` 监听，或 Element Plus 自带）

---

## 6. 触控与可访问性（P1）

```css
@media (max-width: 768px) {
  /* 移动端可点元素最小高度 */
  .el-button,
  .mobile-tabbar .tab-item,
  .topbar-link {
    min-height: 44px;
  }

  /* 表单控件加大，易于单手输入 */
  .el-input__inner,
  .el-textarea__inner {
    font-size: 16px;   /* 防止 iOS 自动放大（<16px 会触发缩放） */
  }
}
```

**iOS 输入框缩放**是移动端最常见的坑：`font-size < 16px` 时 iOS Safari 会自动放大页面，必须 ≥16px。

---

## 7. 实施顺序

| 阶段 | 内容 | 涉及文件 |
|---|---|---|
| **P0-1** | viewport + 安全区 + 全局窄屏基线 + 弹窗全屏 + 表单窄屏 | `index.html`、`style/index.css` |
| **P0-2** | 底部 Tab 导航 + 顶部汉堡菜单 | 新增 `MobileTabBar.vue`、`DefaultLayout.vue` |
| **P0-3** | 下单主链路适配（详情 → 购物车 → 结算 → 支付） | `ProductDetail.vue`、`Cart.vue`、`Settlement.vue`、`Paid.vue` |
| **P0-4** | 个人中心 + 订单列表/详情 | `UserLayout.vue`、`Profile.vue`、`OrderList.vue`、`OrderDetail.vue` |
| **P1-1** | 其余页面（收藏/券/会员/售后/登录/搜索） | 对应 vue 文件 |
| **P1-2** | 触控尺寸 + iOS 16px 字号 | `style/index.css` |

---

## 8. 验收标准

### 设备矩阵

| 设备 | 视口宽度 | 检查点 |
|---|---|---|
| iPhone SE | 375px | 最窄基线，不能横向滚动 |
| iPhone 14 | 390px | 主流 |
| iPhone 14 Pro Max | 430px | 大屏 |
| iPad 竖屏 | 768px | 断点边界 |
| iPad 横屏 | 1024px | 断点边界 |
| 桌面 | 1440px | 不能被移动端样式影响 |

### 逐项检查

- [ ] 375px 下无横向滚动条（除刻意的 `.h-scroll` 容器）
- [ ] 底部固定按钮不被 iPhone 底部横条遮挡
- [ ] 顶部导航在窄屏能访问全部功能（汉堡菜单）
- [ ] 商品详情页「加入购物车/立即购买」始终可见
- [ ] 结算页金额明细不错位
- [ ] 弹窗/抽屉不超出屏幕
- [ ] 表单标签不挤压输入框
- [ ] 点击输入框 iOS 不自动放大
- [ ] 所有可点元素 ≥ 44px
- [ ] 桌面端样式与改动前一致（回归验证）

---

## 9. 工具建议

移动端调试推荐（不改代码，仅辅助）：

- Chrome DevTools → 设备模拟（iPhone 尺寸预设）
- `viewport-fit=cover` 需要真机验证（模拟器看不到刘海）
- Lighthouse 移动端跑分，检查 CLS（布局偏移）和触控目标

---

## 10. 与 AI 客服模块的衔接

商城要做 AI 客服（个人中心 → AI 客服），移动端相关：

| 需求 | 处理 |
|---|---|
| AI 客服入口 | 放**个人中心功能宫格**（移动端也是宫格，不用底部 Tab 挤） |
| 聊天页在移动端 | 全屏独立页面，底部输入框固定 + `safe-bottom` 避让 |
| 商品卡片 | 移动端单列大卡（图片占 40% 宽），比 PC 双列更易点 |
| 流式输出 | 移动端打字机效果同 PC |

**前提**：AI 客服页面必须在 P0-2 的底部 Tab / 汉堡菜单体系里确定入口位置，建议放个人中心宫格（同时在 PC 端个人中心左侧菜单也加一项）。

---

## 附：关键决策记录

| 决策 | 理由 |
|---|---|
| 断点收敛到 768/1024 两档 | 现状 10 档各自为政，维护成本高 |
| 移动端用底部 Tab 而非顶部汉堡做主导航 | 电商移动端标准，拇指可达性 |
| 商品详情底部固定购买栏 | 移动端转化关键 |
| 输入框字号 ≥16px | iOS 自动缩放会导致布局错乱 |
| `viewport-fit=cover` + env() | 刘海屏/底部横条适配 |
| 保留 `.h-scroll` 横向滚动模式 | 比强行换行更省空间，是移动端列表的标准做法 |
