## 收货地址模块

### 接口（4 个，均需登录，`/api/address/**` 走网关 order-service 路由）

| 接口 | 方法 + 路径 | 请求体 | 说明 |
|------|------------|--------|------|
| 地址列表 | `GET /api/address` | — | 返回该用户全部地址（含 `isDefault`）。**默认地址不单独接口**，前端取 `is_default=1` 那条 |
| 新增地址 | `POST /api/address` | consignee/phone/region/detailAddress/isDefault | 第一条自动设默认；传 `isDefault=1` 则设默认 |
| 修改地址 | `PUT /api/address/{id}` | 同上 | 改 `isDefault=1` 即「设为默认」，不单独开接口 |
| 删除地址 | `DELETE /api/address/{id}` | — | 删的是默认地址时，自动把最新一条设为默认 |

### 各层做什么

- **entity** `Address`：对应表全部字段（含 shopId/userId/createdAt/updatedAt）。
- **dto** `AddAddressDTO` / `UpdateAddressDTO`：收前端参数（consignee/phone/region/detailAddress/isDefault）。
- **vo** `AddressVO`：返回前端（id/consignee/phone/region/detailAddress/isDefault/createdAt），**不下发 userId/shopId**。
- **mapper** `AddressMapper` + xml：
  - `listByUser`（id 倒序，最新在前）· `findById`（带 shopId 校验归属）· `insert`（回填自增 id）· `update` · `deleteById`
  - `clearDefault`（把该用户所有地址 `is_default` 清 0）· `setDefaultToLatest`（把 id 最大一条设为默认，删除默认后兜底）
- **service** `AddressService` + impl：
  - 列表映射 VO；新增/修改/删除 + 默认地址逻辑 + 校验
  - 校验四必填 + 手机号 11 位数字；`userId` 从 `UserContext` 取、`shopId` 固定 1
  - `@Transactional` 保证「同一用户只有一个默认」不被并发破坏
- **controller** `AddressController`（`@RequestMapping("/api/address")`）：四个接口，返回统一 `Result<T>`

### 默认地址逻辑（核心）

1. 第一条地址**自动**设为默认；
2. 设默认（`is_default=1`）时先 `clearDefault` 清掉其他默认，再落新值；
3. 删除默认地址后，自动把最新一条（id 最大）`setDefaultToLatest` 设为默认；
4. 「设为默认」通过 `PUT /api/address/{id}` 改 `is_default=1` 实现，不单独开接口；
5. 以上都在 Service 层 + 事务内完成。

### 前端设计

- **省市区级联**：用微信原生 `picker mode="region"`（数据微信内置、自动三级级联、零包体积），选中数组 `["省","市","区"]` 提交时 `join(' ')` 存进 `region`。不拆字段、不建省市区表。
- **接口层** `api/address.js`：`listAddress` / `addAddress` / `updateAddress` / `deleteAddress`。
- **列表页**：展示每条地址（收货人 + 手机号 + 省市区 + 详细地址 + 默认标签），提供编辑 / 删除 / 设为默认。
- **编辑页**：表单（收货人、手机号、省市区 picker、详细地址、设为默认开关），新增和编辑复用同一表单。
