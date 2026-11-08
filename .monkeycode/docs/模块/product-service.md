# product-service

商品、内容、库存与部分上传。

## 结构

```
product-service/src/main/java/ecommerce/product/
├── controller/     # C 端 + Admin* + InternalAi + Stock*
├── service/
└── mapper/
resources/mappers/product|stock/
```

## 依赖

MySQL、Nacos、common、磁盘图片。被网关、AI 内部接口、商城首页依赖。
