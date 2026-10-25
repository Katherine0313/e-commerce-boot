# 后端生产部署（8G / 小程序优先）

目标：先上线微信小程序所需后端；后台管理端过几天再部署。
后台管理端复用同一套后端，不需要再起服务。

## 1. 本地打包

在 `e-commerce-boot` 根目录：

```bash
./mvnw clean package -DskipTests
```

把各模块 `target/` 下的可执行 jar 上传到服务器，并改成统一名字：

```text
auth-service/target/auth-service-0.0.1-SNAPSHOT.jar        -> /www/java/auth-service/auth-service.jar
shop-service/target/shop-service-0.0.1-SNAPSHOT.jar        -> /www/java/shop-service/shop-service.jar
product-service/target/product-service-0.0.1-SNAPSHOT.jar  -> /www/java/product-service/product-service.jar
order-service/target/order-service-0.0.1-SNAPSHOT.jar      -> /www/java/order-service/order-service.jar
aftersale-service/target/aftersale-service-*.jar           -> /www/java/aftersale-service/aftersale-service.jar
user-service/target/user-service-0.0.1-SNAPSHOT.jar        -> /www/java/user-service/user-service.jar
file-service/target/file-service-0.0.1-SNAPSHOT.jar        -> /www/java/file-service/file-service.jar
ai-agent-service/target/ai-agent-service-*.jar             -> /www/java/ai-agent-service/ai-agent-service.jar
gateway/target/gateway-0.0.1-SNAPSHOT.jar                  -> /www/java/gateway/gateway.jar
```

## 2. 服务器目录

把仓库 `deploy/` 下的文件按下面映射上传：

```text
deploy/docker-compose.yml -> /www/java/docker-compose.yml
deploy/.env.example       -> /www/java/.env.example
deploy/Dockerfile         -> /www/java/Dockerfile
deploy/nginx-api.conf     -> 参考用，内容合并到宝塔站点配置
deploy/README.md          -> /www/java/README.md（可选）
```

最终目录：

```text
/www/java/
├── docker-compose.yml
├── Dockerfile
├── .env.example
├── .env                    # 从 .env.example 复制后填真实值
├── upload/                 # 商品图 + 头像，必须存在
├── auth-service/
│   ├── Dockerfile
│   └── auth-service.jar
├── shop-service/
├── product-service/
├── order-service/
├── aftersale-service/
├── user-service/
├── file-service/
├── ai-agent-service/
└── gateway/
```

操作：

```bash
cd /www/java
mkdir -p upload
cp .env.example .env
# 编辑 .env 填真实值
for d in auth-service shop-service product-service order-service aftersale-service user-service file-service ai-agent-service gateway; do
  cp Dockerfile "$d/Dockerfile"
done
```

> ⚠️ 把本地 `e-commerce-boot/upload/` **整个目录**上传到 `/www/java/upload/`。
> 里面包含商品图 `image/upload/`、详情图 `image/body/`、分类图 `image/category/`、
> 轮播图 `image/swiper/`、分享图 `image/share/`、二维码/店铺 logo 等；缺了小程序图片会 404。

## 3. 宝塔 MySQL 准备

MySQL 仍在宝塔宿主机，容器通过 `host.docker.internal` 访问，所以：

- MySQL 绑定 `0.0.0.0`（或 Docker 网桥 IP），并给 Docker 网段授权
- **3306 不要暴露公网**
- MySQL 里要有完整表结构和数据；`DB_URL` 的库名要对应

> Redis 已经改成 Docker 容器（见 `docker-compose.yml` 的 `redis` 服务），
> 只在 `my-net` 内网暴露、不映射宿主机端口，密码取 `.env` 的
> `SPRING_DATA_REDIS_PASSWORD`。宿主机不需要再装宝塔 Redis。

## 4. 启动顺序

```bash
cd /www/java

# 1. 先构建镜像
docker compose build

# 2. 先起 Redis + Nacos
docker compose up -d redis nacos
docker logs -f nacos        # 看到 Nacos started successfully 再继续

# 3. 再起全部服务
docker compose up -d

# 4. 查看
docker ps
free -h
docker stats --no-stream
```

如果内存紧张，先 `docker compose stop ai-agent-service`，客服会退化为本地规则 + 人工，不影响小程序主链路。

## 5. 验证

```bash
# 网关直连
curl http://127.0.0.1:8080/api/products/1

# Nacos 控制台（只建议内网/限制 IP 访问）
# http://服务器IP:18080
```

Nacos 控制台里应该能看到 9 个服务实例。

## 6. Nginx + HTTPS

宝塔建站 `api.jiemids.com`，申请 SSL，然后把 `deploy/nginx-api.conf` 里的 location 配置合并进去。

关键点：

- `proxy_pass http://127.0.0.1:8080`
- `/api/` 下 `proxy_buffering off`（AI SSE）
- `client_max_body_size 60m`（视频上传）
- `/image/`、`/upload/` 也要反代，否则商品图和头像 404

## 7. 小程序

- `utils/server-api.js` 生产地址已经是 `https://api.jiemids.com`
- HBuilderX 用「发行 → 小程序-微信」构建
- ⚠️ 构建产物在 `unpackage/dist/build/mp-weixin`，开发者工具**必须打开这个目录**再上传；
  `unpackage/dist/dev/mp-weixin` 是开发包，接口地址是 `http://127.0.0.1:8080`，上传会导致审核白屏、商品全空
- 微信后台配置：
  - request 合法域名：`https://api.jiemids.com`
  - uploadFile：`https://api.jiemids.com`
  - downloadFile：`https://api.jiemids.com`
- 真机测试登录、商品、下单、支付、AI 客服

## 8. 后台管理端（过几天）

后端 `auth-service` / `shop-service` 已经一起起来了，到时只需要：

- 构建 `e-commerce` 的 `dist`
- 宝塔建站 `admin.jiemids.com`
- Nginx 加 `/api/`、`/image/`、`/upload/`、`/ws/` 反代
- `/ws/` 必须带 `Upgrade` 头，否则来单提醒连不上

## 9. 说明

- Python 推荐服务没有包含；`product-service` 调不到 9009 时会自动走热销兜底，首页不会白屏。需要个性化推荐再单独加容器。
- `file-service` 目前是空骨架，不影响主链路；不想跑可以把它从 `docker-compose.yml` 和 `gateway` 的 `depends_on` 里删掉。
- 后台管理端过几天部署，后端 `auth-service` / `shop-service` 已经在这里一起起来了。

## 10. 必查清单

- [ ] `JWT_SECRET` 所有服务一致
- [ ] `SILICONFLOW_API_KEY` 是真实 key（本地那份含 `xxx` 占位）
- [ ] `WECHAT_SECRET` 正确
- [ ] `DB_URL` 指向正确的库，且库里有表和数据
- [ ] `/www/java/upload` 存在且可写，且已上传现有图片（`image/upload`、`body`、`category`、`swiper`、`share`、`QRcode.webp`、`store-logo.webp` 等）
- [ ] 时区是 `Asia/Shanghai`（Dockerfile 已带）
- [ ] 只放行 22/80/443；Nacos 控制台限制来源 IP
- [ ] 3306 不暴露公网；Redis 容器没有 `ports` 映射
- [ ] `REDIS_HOST=redis`、`SPRING_DATA_REDIS_PASSWORD` 已填；`docker exec redis redis-cli -a '密码' ping` 返回 PONG
- [ ] `docker compose ps` 全部 Up
- [ ] `https://api.jiemids.com/api/products/1` 能返回商品
- [ ] `curl -o /dev/null -w "%{time_total}s\n" http://127.0.0.1:8080/api/categories` 在毫秒级（说明 Redis 命中，没走降级）
