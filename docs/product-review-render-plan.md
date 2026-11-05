# 商品详情「评价 / 商品参数 / 图文详情」C 端渲染方案

> 适用范围：**C 端 PC/H5 商城（Vue3）和微信小程序**，不涉及后台管理系统。
> 目标：商品详情页按淘宝样式做三个 Tab —— 商品评价、商品参数、图文详情（评价在前，参数其次，图文详情最后），并复用现有评价数据。

---

## 1. 数据现状

| 项 | 现状 |
|---|---|
| 商品 | 136 个，全部上架 |
| 评价 | 每个商品 2 条虚拟评价，共 272 条（另有少量真实评价） |
| 星级 | 5 星 130、4 星 129、3 星 7、2 星 6，前端「好评/中评/差评」有内容可切 |
| 晒图 | 14 个品类 × 2 张买家晒图，共 28 张；主评价用 `-1`，副评价用 `-2`，同一商品两条评价图片不同 |
| 标签 | 当前只有 4 个（质量很好、性价比高、中规中矩、有待改进），定稿为固定 8 个，见第 12 节 |
| 图片位置 | `/image/review/buyer-{category}-{1|2}.webp`，已同步到生产 |
| 种子脚本 | `docs/sql/20261105_seed_product_reviews.sql`，可重复执行 |

评价走的是后台已有的虚拟评价机制（`is_virtual=1`、`user_id=0`）。C 端展示时把昵称显示为「匿名买家」，不要显示「系统评价」字样。

---

## 2. 页面整体结构

### 2.1 商城 PC 端：左右两栏，右栏固定

```
┌───────────────────────────────┬──────────────────────┐
│ 左栏（长条，随页面滚动）        │ 右栏（sticky 固定）   │
│                               │                      │
│  主图轮播                      │  价格 / 划线价        │
│                               │  标题 / 副标题        │
│  吸顶 Tab                     │  促销标签             │
│  商品评价 | 商品参数 | 图文详情 │  规格选择             │
│                               │  数量 / 库存          │
│  对应面板内容（长列表）          │  加入购物车 / 立即购买 │
│                               │  服务承诺             │
└───────────────────────────────┴──────────────────────┘
```

- 左栏 `flex: 1`，承载主图、Tab 和三个面板，内容再长也随页面滚动。
- 右栏固定宽度（建议 380~420px），`position: sticky; top: 16px; align-self: flex-start`，滚动时始终可见。
- 右栏自身内容超出视口时，内部滚动：`max-height: calc(100vh - 32px); overflow-y: auto;`。
- Tab 在左栏顶部吸顶（`position: sticky; top: 0`），不遮右栏。

### 2.2 商城 H5 / 小程序：单栏 + 底部购买栏

- 主图、Tab、面板单栏排列。
- 购买按钮不跟随右栏，改为底部固定操作条：`position: fixed; bottom: 0`，加 `padding-bottom: env(safe-area-inset-bottom)`。
- 页面内容底部要留出购买条高度的 padding，避免最后一条评价被遮挡。

### 2.3 Tab 交互

- 默认选中「商品评价」。
- Tab 只切面板，不跳页；每个面板懒加载，切过一次后缓存，不重复请求。

---

## 3. 接口与字段

| 用途 | 接口 | 说明 |
|---|---|---|
| 商品详情 | `GET /api/products/{id}` | 免登录 |
| 评价统计 | `GET /api/reviews/product/{id}/summary` | 免登录 |
| 评价列表 | `GET /api/reviews/product/{id}?page=1&pageSize=10&tags=质量很好,物流很快` | 免登录，`tags` 可选，最多 4 个 |
| 评价标签 | `GET /api/reviews/product/{id}/tags` | 免登录，返回该商品 8 个固定标签及各自条数 |

统一响应结构：

```json
{ "success": true, "data": {}, "message": null, "code": null }
```

前端统一取 `data`。

### 3.1 商品详情字段（ProductVO）

| 字段 | 用途 |
|---|---|
| `name/subTitle` | 标题、副标题 |
| `brand` | 品牌 |
| `image` | 主图 |
| `price/originalPrice/marketPrice` | 售价、划线价、市场价 |
| `promotionTag` | 促销标签 |
| `stock` | 库存 |
| `productCode` | 商品编码 |
| `body` | 图文详情（富文本） |
| `specs` | 商品参数，`[{ name, value }]` 有序数组 |

### 3.2 评价统计字段（ReviewSummaryVO）

| 字段 | 用途 |
|---|---|
| `totalCount` | 评价总数 |
| `avgRating` | 平均星级（保留 1 位） |
| `goodRate` | 好评率（0~1） |
| `withMediaCount` | 有图/视频评价条数 |
| `fiveStar` ~ `oneStar` | 各星级条数，用于分布条 |

### 3.3 评价列表字段（ReviewDetailVO）

| 字段 | 用途 |
|---|---|
| `id` | 评价 ID |
| `userName` | 昵称 |
| `userAvatar` | 头像，空则用默认头像 |
| `isVirtual` | 1 时昵称显示「匿名买家」 |
| `rating` | 1~5 星 |
| `tags` | 标签数组，0~4 个，取值见第 12 节；前端逐个渲染胶囊 |
| `content` | 评价正文 |
| `images` | **JSON 字符串**，需 `JSON.parse` |
| `reply` / `replyTime` | 商家回复 |
| `createdAt` | 评价时间 |

分页结构：`data.list[]`、`data.total`、`data.page`、`data.pageSize`。

---

## 4. 商品评价 Tab

### 4.1 汇总卡片

```
┌─────────────────────────────────────────┐
│  4.5          ★★★★★                      │
│  平均评分      好评率 100%                 │
│              5星 ██████████ 130           │
│              4星 ██████████ 129           │
│              3星 █            7           │
│              2星 █            6           │
│              1星              0           │
│  有图评价 137 条  >                       │
└─────────────────────────────────────────┘
```

- 左侧：`avgRating` 大号数字 + 星级 + `goodRate * 100` 好评率。
- 右侧：每行星级条，宽度 = `该星数量 / totalCount * 100%`。
- 底部：「有图评价 {withMediaCount} 条」，点击切到有图筛选。

### 4.2 筛选条

评价区顶部有两层筛选，顺序参考淘宝：

**第一层：评价类型**

全部 / 有图 / 好评 / 中评 / 差评

**第二层：标签云**

8 个固定标签全部平铺展示，不放下拉，带条数，可点击筛选；支持多选（最多 4 个，OR 命中），详见第 12 节。

**当前后端只支持 `page`、`pageSize`，不支持星级、有图、标签筛选。**
一期建议先做「全部 / 有图 + 标签云展示」，筛选等后端参数补齐后再接。每商品只有 2 条评价，一期也可先本地过滤，不会分页错位。

要做完整筛选，后端需要补三个可选参数：

```text
ratingType=good|mid|bad     good=4~5 星，mid=3 星，bad=1~2 星
hasMedia=true|false
tags=质量很好,物流很快    最多 4 个，OR 匹配（含任一标签即命中）
```

改动点在 `ProductReviewMapper` 的查询条件，`summary` 不用动。

### 4.3 评价卡片

```
[头像] 匿名买家          ★★★★☆  2026-10-28
        [质量很好] [物流很快] [有图]
        手机拍照很出片，夜景也稳，续航一天没问题……
        [图1] [图2] [图3]
        ┌ 商家回复：感谢支持，有问题随时联系客服 ─┐
```

- 头像：`userAvatar` 为空时用昵称首字圆形色块。
- 昵称：`isVirtual === 1 ? '匿名买家' : userName`。
- 星级：`rating` 为整数，渲染 5 个星，实心/空心。
- 标签：`tags` 数组逐个渲染浅橙胶囊，最多 4 个；为空则不显示。
- 图片：最多展示 9 张，3 列九宫格，点击预览。
- 商家回复：`reply` 存在才渲染，浅灰底。
- 时间：`createdAt` 取日期部分。

图片解析与路径拼接（两端都要做）：

```js
const media = JSON.parse(item.images || '[]')
const url = media[0]?.url?.startsWith('http')
  ? media[0].url
  : IMG_BASE + media[0].url
```

`IMG_BASE` 配成生产文件域名；`images[].url` 是 `/image/review/buyer-phone-1.webp` 这种相对路径。

### 4.4 分页

- 首屏 `page=1, pageSize=10`，滚动到底加载下一页。
- `list.length >= total` 时显示「没有更多了」。
- 当前每个商品只有 2 条，首屏即到底，但代码按分页写，后续真实评价多了不用改。

---

## 5. 商品参数 Tab

`specs` 是有序数组，直接两列表格：

```
品牌        苹果
商品编码    iPhone15Pro-256
屏幕尺寸    6.1英寸
处理器      A17 Pro
运行内存    8GB
```

- 左侧参数名（灰），右侧参数值（黑）。
- 按数组顺序渲染，不要自己排序。
- 可额外补 `brand`、`productCode`、`stock` 三项基础信息。
- `specs` 为空时显示「暂无参数」。

---

## 6. 图文详情 Tab

直接渲染 `body` 富文本：

- 商城：`v-html="body"`
- 小程序：`<rich-text nodes="{{body}}" />`

**关键点**：`body` 内的商品图如果是相对路径（`src="/image/..."`），渲染前要替换为绝对地址，否则小程序真机一定裂图。

```js
function fixBody(html) {
  return (html || '').replace(/src="(\/image\/[^"]+)"/g, `src="${IMG_BASE}$1"`)
}
```

`body` 为空时显示「暂无图文详情」，不要留空白面板。

---

## 7. 商城（Vue3）实现

### 7.1 目录建议

```
src/pages/product/detail.vue          # 详情页
src/pages/product/components/
  ProductTabs.vue                     # 吸顶 Tab 容器
  ReviewPanel.vue                     # 评价面板
  ReviewSummary.vue                   # 评分汇总
  ReviewItem.vue                      # 单条评价
  ReviewGallery.vue                   # 九宫格 + 预览
  SpecPanel.vue                       # 商品参数
  DetailPanel.vue                     # 图文详情
src/api/product.js
src/api/review.js
```

### 7.2 页面逻辑

```js
const product = ref(null)
const summary = ref(null)
const reviews = ref([])
const total = ref(0)
const page = ref(1)
const activeTab = ref('review')
const loadedTabs = new Set(['review'])

onMounted(async () => {
  product.value = (await getProduct(id)).data
  summary.value = (await getReviewSummary(id)).data
  await loadReviews()
})

async function loadReviews() {
  const { data } = await getReviews(id, { page: page.value, pageSize: 10 })
  reviews.value = page.value === 1 ? data.list : [...reviews.value, ...data.list]
  total.value = data.total
}
```

- 图片预览用项目现有预览组件；没有就自研遮罩层。
- 滚动加载用 `IntersectionObserver` 监听列表底部。
- PC 端左右两栏：左边主图 + Tab + 面板随页面滚动，右边购买面板 sticky 固定。
- Tab 吸顶：`position: sticky; top: 0; z-index: 10`。

### 7.3 PC 端左右分栏与右栏固定

结构：

```vue
<div class="detail-layout">
  <!-- 左栏：长条滚动 -->
  <div class="left-col">
    <ProductGallery :images="product.images" />
    <ProductTabs :active="activeTab" :total="summary.totalCount" @change="switchTab" />
    <ReviewPanel v-if="activeTab === 'review'" ... />
    <SpecPanel v-else-if="activeTab === 'spec'" ... />
    <DetailPanel v-else ... />
  </div>

  <!-- 右栏：购买面板，滚动时固定 -->
  <aside class="buy-panel">
    <div class="price">...</div>
    <div class="title">...</div>
    <div class="sku">...</div>
    <div class="qty">...</div>
    <div class="actions">加入购物车 / 立即购买</div>
    <div class="service">...</div>
  </aside>
</div>
```

样式：

```css
.detail-layout {
  display: flex;
  align-items: flex-start;
  gap: 24px;
}

.left-col {
  flex: 1;
  min-width: 0; /* 防止长内容撑破 flex */
}

.buy-panel {
  width: 400px;
  flex: 0 0 400px;
  position: sticky;
  top: 16px;
  align-self: flex-start;
  max-height: calc(100vh - 32px);
  overflow-y: auto;
}

@media (max-width: 1024px) {
  .detail-layout { display: block; }
  .buy-panel {
    position: static;
    width: auto;
    max-height: none;
    overflow: visible;
  }
  /* 购买按钮改成底部固定条 */
}
```

注意：

- `position: sticky` 的祖先链不能有 `overflow: hidden`，否则会失效；有滚动容器时 sticky 只在该容器内生效。
- 右栏不要放长内容（评论、详情），只放价格、规格、购买；否则固定后可视区域不够。
- 右栏高度超过视口时自身滚动，保证购买按钮始终可达。

### 7.4 移动端底部购买条

```css
.buy-bar {
  position: fixed;
  left: 0;
  right: 0;
  bottom: 0;
  z-index: 20;
  display: flex;
  background: #fff;
  padding: 8px 12px calc(8px + env(safe-area-inset-bottom));
  box-shadow: 0 -2px 8px rgba(0, 0, 0, 0.06);
}
```

页面容器加 `padding-bottom: 64px`（按购买条实际高度调整）。

---

## 8. 微信小程序实现

### 8.1 目录建议

```
pages/product/detail/
  index.wxml / index.js / index.wxss
components/
  review-summary/
  review-item/
  review-gallery/
```

### 8.2 WXML 结构

```xml
<view class="tabs">
  <view class="tab {{activeTab==='review'?'active':''}}" bindtap="switchTab" data-tab="review">
    商品评价({{summary.totalCount}})
  </view>
  <view class="tab {{activeTab==='spec'?'active':''}}" bindtap="switchTab" data-tab="spec">商品参数</view>
  <view class="tab {{activeTab==='detail'?'active':''}}" bindtap="switchTab" data-tab="detail">图文详情</view>
</view>

<view wx:if="{{activeTab==='review'}}">
  <review-summary summary="{{summary}}" bind:media="filterMedia" />
  <review-item wx:for="{{reviews}}" wx:key="id" item="{{item}}" bind:preview="previewImage" />
  <view wx:if="{{noMore}}">没有更多了</view>
</view>

<view wx:if="{{activeTab==='spec'}}">
  <view wx:for="{{specs}}" wx:key="name" class="spec-row">
    <text class="spec-name">{{item.name}}</text>
    <text class="spec-value">{{item.value}}</text>
  </view>
</view>

<view wx:if="{{activeTab==='detail'}}">
  <rich-text nodes="{{body}}" wx:if="{{body}}"></rich-text>
  <view wx:else>暂无图文详情</view>
</view>
```

### 8.3 关键 JS

```js
previewImage(e) {
  const { urls, current } = e.detail
  wx.previewImage({ current, urls })
}

onReachBottom() {
  if (this.data.activeTab === 'review' && !this.data.noMore) {
    this.loadReviews(this.data.page + 1)
  }
}
```

### 8.4 注意事项

- `images` 同样要 `JSON.parse`。
- `rich-text` 渲染前替换 `body` 里的相对路径图片。
- 吸顶优先用 `position: sticky`；父容器不能 `overflow: hidden`，不兼容时改 `fixed + 占位块`。
- 小程序后台需把图片域名配置到 `downloadFile` 合法域名，否则加载和预览会失败。
- 评价图片使用 `lazy-load`。

---

## 9. 两端差异对照

| 点 | 商城 | 小程序 |
|---|---|---|
| Tab 吸顶 | CSS sticky | sticky，兼容差时 fixed + 占位 |
| 富文本 | `v-html` | `<rich-text nodes>` |
| 图片预览 | 自研预览层/组件 | `wx.previewImage` |
| 加载更多 | IntersectionObserver | `onReachBottom` |
| 图片域名 | 拼接 env 变量 | 拼接 baseUrl + 配置合法域名 |
| 滚动容器 | 页面滚动 | 页面滚动或 scroll-view |
| 购买区 | 右栏 sticky 固定，左侧长条滚动 | 底部固定购买条 |

---

## 10. 加载与空态

| 场景 | 表现 |
|---|---|
| 商品详情请求中 | 骨架屏 |
| `summary.totalCount === 0` | 「暂无评价，期待你的第一条评价」 |
| 评价图片失败 | 灰色占位块，不显示破图 |
| 参数为空 | 「暂无参数」 |
| 图文详情为空 | 「暂无图文详情」 |
| 接口失败 | toast + 重试按钮，保留已有数据 |

---

## 11. 落地顺序与验收

### 落地顺序

1. 商城详情页先做 Tab 外壳 + 评价汇总/列表（数据已就绪）。
2. 后端新增标签枚举、`GET /api/reviews/product/{id}/tags`、`tags` 筛选参数、DTO 校验。
3. 数据迁移（「中规中矩」→「有待改进」），本地执行后同步生产。
4. 商城接入标签云（8 个平铺、带数量、点击筛选）。
5. 后端补 `ratingType`、`hasMedia` 参数，再接星级筛选。
6. 小程序复用同一套字段和结构，重点处理 `rich-text`、标签两行布局和图片域名。
7. 两端联调验收。

### 验收清单

- [ ] Tab 三个面板可切换，切回不重复请求。
- [ ] 每个商品展示 2 条评价，两条图片不同。
- [ ] 汇总卡片平均分、好评率、星级分布与接口一致。
- [ ] 评价图片九宫格可点击预览。
- [ ] 虚拟评价昵称显示「匿名买家」，不出现「系统评价」。
- [ ] 图文详情内图片能正常显示（相对路径已替换）。
- [ ] 商品参数按 `specs` 原顺序展示。
- [ ] 小程序真机图片可加载、可预览。
- [ ] 无评价/无详情/无参数时都有空态提示。
- [ ] PC 端滚动左侧主图和评价时，右侧购买面板保持可见。
- [ ] 右栏内容超出视口时内部可滚动，购买按钮不被裁掉。
- [ ] 移动端底部购买条不遮挡最后一条评价。
- [ ] 8 个标签全部平铺展示、带数量，不是下拉选择。
- [ ] 点击标签能筛选评价，再点一次取消筛选。
- [ ] 写评价时标签可多选，最多 4 个，选满后其余标签不可选并提示。
- [ ] 评价卡片最多展示 4 个标签胶囊。
- [ ] 标签筛选支持多选（OR），最多 4 个。
- [ ] 小程序标签云正好两行、每行 4 个，不换行错位。

---

## 12. 评价标签规范（8 个固定标签，最多选 4 个）

### 12.1 定稿 8 个标签

4 个正向 + 4 个负向，正好在小程序里排成两行、每行 4 个：

| 标签 | 情感 | 适用场景 |
|---|---|---|
| 质量很好 | 正向 | 做工、品质、耐用 |
| 性价比高 | 正向 | 价格划算、值这个价 |
| 物流很快 | 正向 | 发货快、到货快 |
| 服务周到 | 正向 | 客服、售后、解答及时 |
| 有待改进 | 负向 | 整体不满意、体验一般 |
| 物流慢 | 负向 | 发货慢、运输慢 |
| 包装破损 | 负向 | 外包装挤压、破损 |
| 与描述不符 | 负向 | 实物与详情页不一致 |

取舍说明：

- 去掉「包装完好」，与「包装破损」是同一维度，只保留负向；
- 去掉「中规中矩」「符合预期」，中性标签筛选价值低，还占位置；
- 保留「服务周到」，售后是电商评价高频维度。

### 12.2 多选规则（重点）

- 一条评价可以同时打 **0~4 个标签**，不是只能选一个。
- 同一标签不能重复选择。
- 选满 4 个后，其余标签置灰不可点，并提示「最多选择 4 个标签」。
- 标签独立于星级，不按星级自动推导。
- 写评价：8 个标签全部平铺、多选，不用下拉。
- 评价列表：按顺序展示该评价的全部标签，最多 4 个胶囊。
- 标签筛选：支持多选，**OR 语义**（包含任一选中标签即命中），同样最多 4 个，再点一次取消。

### 12.3 数据存储调整

现有 `tag varchar(64)` 是单值，已改为多值 `tags JSON`；旧 `tag` 列已完成迁移并删除。

迁移分两步执行（均已在本地和生产完成）：

```sql
-- 1) 新增多值字段，历史单值转数组，然后清空旧字段
ALTER TABLE product_reviews
  ADD COLUMN tags JSON NULL COMMENT '评价标签数组，最多 4 个' AFTER tag;

UPDATE product_reviews
SET tags = JSON_ARRAY(tag)
WHERE tag IS NOT NULL AND tag <> '';

UPDATE product_reviews SET tag = NULL;

-- 2) 新版 order-service 发布后，删除旧列
ALTER TABLE product_reviews DROP COLUMN tag;
```

- 生产 MySQL 5.7.44 支持 JSON 类型；
- 迁移脚本：[20261105_review_tags_multi.sql](sql/20261105_review_tags_multi.sql)、[20261105_drop_review_tag.sql](sql/20261105_drop_review_tag.sql)；
- 本地执行后必须同步生产，避免线上表结构残缺。

### 12.4 后端接口与校验

- order-service 新增 `ReviewTagEnum`，固定上述 8 个值，标签值直接存中文，不做 code 映射。
- 实体 `ProductReview` 只有 `tags: String`（JSON 文本），不再保留旧 `tag`。
- `CreateReviewDTO`、`AdminReviewDTO` 的 `tag` 改为 `List<String> tags`，校验：
  - 数量 ≤ 4；
  - 去重；
  - 每个值必须属于 8 个固定标签。
- `ReviewDetailVO` 返回 `tags` 数组。
- 评价列表查询参数用 `tags=质量很好,物流很快`，最多 4 个，SQL 用 `JSON_CONTAINS` 做 OR 匹配。
- 新增 `GET /api/reviews/product/{id}/tags`，返回 8 个固定标签及各自条数，数量为 0 也返回，前端置灰显示。
- 后台评价编辑：8 个标签平铺多选，最多 4 个，不用下拉。

标签接口返回示例：

```json
[
  { "value": "质量很好", "label": "质量很好", "count": 130 },
  { "value": "性价比高", "label": "性价比高", "count": 129 },
  { "value": "物流很快", "label": "物流很快", "count": 98 },
  { "value": "服务周到", "label": "服务周到", "count": 76 },
  { "value": "有待改进", "label": "有待改进", "count": 13 },
  { "value": "物流慢", "label": "物流慢", "count": 12 },
  { "value": "包装破损", "label": "包装破损", "count": 5 },
  { "value": "与描述不符", "label": "与描述不符", "count": 3 }
]
```

统计口径：一条评价有 N 个标签时，每个标签各计 1 次，所以标签数量之和可能大于评价总数。

### 12.5 C 端渲染

**标签云筛选（商城）**

```vue
<div class="tag-cloud">
  <button
    v-for="t in tagList"
    :key="t.value"
    class="tag-item"
    :class="{
      active: selectedTags.includes(t.value),
      disabled: !selectedTags.includes(t.value) && selectedTags.length >= 4
    }"
    @click="toggleTag(t.value)"
  >
    {{ t.label }}<span v-if="t.count" class="count">{{ t.count }}</span>
  </button>
</div>
<p class="tip">已选 {{ selectedTags.length }}/4</p>
```

```js
const selectedTags = ref([])

function toggleTag(value) {
  const i = selectedTags.value.indexOf(value)
  if (i > -1) {
    selectedTags.value.splice(i, 1)
  } else if (selectedTags.value.length < 4) {
    selectedTags.value.push(value)
  } else {
    message.warning('最多选择 4 个标签')
  }
  page.value = 1
  loadReviews()
}
```

**写评价（商城）**

```vue
<div class="tag-picker">
  <button
    v-for="t in tagOptions"
    :key="t.value"
    class="tag-item"
    :class="{
      active: form.tags.includes(t.value),
      disabled: !form.tags.includes(t.value) && form.tags.length >= 4
    }"
    @click="toggleTag(t.value)"
  >
    {{ t.label }}
  </button>
</div>
<p class="tip">已选 {{ form.tags.length }}/4</p>
```

**标签云（小程序）**

```xml
<view class="tag-cloud">
  <view
    wx:for="{{tagList}}"
    wx:key="value"
    class="tag-item {{item.selected ? 'active' : ''}} {{item.disabled ? 'disabled' : ''}}"
    bindtap="toggleTag"
    data-value="{{item.value}}"
  >
    {{ item.label }}<text wx:if="{{item.count}}" class="count">{{ item.count }}</text>
  </view>
</view>
```

```js
// WXML 不支持 includes/indexOf，选中态先在 JS 里算好
refreshTagState() {
  const selected = this.data.selectedTags
  const tagList = this.data.tagList.map(t => ({
    ...t,
    selected: selected.indexOf(t.value) > -1,
    disabled: selected.indexOf(t.value) === -1 && selected.length >= 4
  }))
  this.setData({ tagList })
}

toggleTag(e) {
  const value = e.currentTarget.dataset.value
  const tags = this.data.selectedTags.slice()
  const i = tags.indexOf(value)
  if (i > -1) {
    tags.splice(i, 1)
  } else if (tags.length < 4) {
    tags.push(value)
  } else {
    wx.showToast({ title: '最多选择 4 个标签', icon: 'none' })
    return
  }
  this.setData({ selectedTags: tags, page: 1 }, () => this.refreshTagState())
  this.loadReviews()
}
```

**两行四列布局**

```css
.tag-cloud {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
}

.tag-item {
  width: calc(25% - 6px);
  box-sizing: border-box;
  text-align: center;
  padding: 6px 0;
  border-radius: 14px;
  background: #f5f5f5;
  font-size: 12px;
  color: #333;
}

.tag-item.active {
  background: #fff7e6;
  color: #ff5000;
  border: 1px solid #ff5000;
}

.tag-item.disabled {
  opacity: 0.4;
}
```

### 12.6 种子数据调整

种子脚本同步改为多标签，示例：

- 5 星主评价：`["质量很好","物流很快"]`
- 4 星副评价：`["性价比高","服务周到"]`
- 3 星中评：`["有待改进","物流慢"]`
- 2 星差评：`["与描述不符","包装破损"]`

种子脚本与迁移 SQL 都在本地执行后同步生产，保证两端数据一致。

### 12.7 验收

- [ ] 写评价可选 0~4 个标签，选第 5 个时无法选中并提示。
- [ ] 同一条评价最多显示 4 个标签胶囊。
- [ ] 标签筛选可同时选多个，最多 4 个，OR 命中。
- [ ] 每个标签的数量口径正确：多标签评价在每个标签下各计一次。
- [ ] 8 个标签全部平铺展示，数量为 0 时置灰，不用下拉。
