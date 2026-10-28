# 后端生产部署（8G / 小程序优先）

目标：先上线微信小程序所需后端；后台管理端过几天再部署。
后台管理端复用同一套后端，不需要再起服务。

## 1. 本地打包

在 `e-commerce-boot` 根目录：

```bash
./mvnw clean package -DskipTests
```

把各模块 `target/` 下的可执行 jar 上传到服务器，并改成统一名字（如果使用镜像仓库，见第 11 节，可跳过手工上传）：

```text
auth-service/target/auth-service-0.0.1-SNAPSHOT.jar        -> /www/java/auth-service/auth-service.jar
shop-service/target/shop-service-0.0.1-SNAPSHOT.jar        -> /www/java/shop-service/shop-service.jar
product-service/target/product-service-0.0.1-SNAPSHOT.jar  -> /www/java/product-service/product-service.jar
order-service/target/order-service-0.0.1-SNAPSHOT.jar      -> /www/java/order-service/order-service.jar
aftersale-service/target/aftersale-service-*.jar           -> /www/java/aftersale-service/aftersale-service.jar
user-service/target/user-service-0.0.1-SNAPSHOT.jar        -> /www/java/user-service/user-service.jar
file-service/target/file-service-0.0.1-SNAPSHOT.jar        -> /www/java/file-service/file-service.jar
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
├── ai-service/             # Python 导购机器人（源码来自 recommender-python）
└── gateway/
```

操作：

```bash
cd /www/java
mkdir -p upload
cp .env.example .env
# 编辑 .env 填真实值
for d in auth-service shop-service product-service order-service aftersale-service user-service file-service gateway; do
  cp Dockerfile "$d/Dockerfile"
done
```

> ⚠️ 把本地 `e-commerce-boot/upload/` **整个目录**上传到 `/www/java/upload/`。
> 里面包含商品图 `image/upload/`、详情图 `image/body/`、分类图 `image/category/`、
> 轮播图 `image/swiper/`、分享图 `image/share/`、二维码/店铺 logo 等；缺了小程序图片会 404。

> ⚠️ Python 导购机器人：把 `recommender-python/` 下的 `app/`、`requirements.txt`、`Dockerfile`、
> `.dockerignore` 上传到 `/www/java/ai-service/`（不要传 `.venv/` 和 `.env`）。
> 它使用自己的 Dockerfile，不要用上面那个通用 Java Dockerfile 覆盖。

## 3. 宝塔 MySQL 准备

MySQL 仍在宝塔宿主机，容器通过 `host.docker.internal` 访问，所以：

- MySQL 绑定 `0.0.0.0`（或 Docker 网桥 IP），并给 Docker 网段授权
- **3306 不要暴露公网**
- MySQL 里要有完整表结构和数据；`DB_URL` 的库名要对应

> Redis 已经改成 Docker 容器（见 `docker-compose.yml` 的 `redis` 服务），
> 只在 `my-net` 内网暴露、不映射宿主机端口，密码取 `.env` 的
> `SPRING_DATA_REDIS_PASSWORD`。宿主机不需要再装宝塔 Redis。

进入 Redis 命令行（密码从 `.env` 读取，不在文档里写明文）：

```bash
cd /www/java
docker exec -it redis redis-cli -a "$(grep '^SPRING_DATA_REDIS_PASSWORD=' .env | cut -d= -f2-)"
```

进去后常用命令：

```text
INFO                 # 运行状态、命中率、内存
TTL category:parent:ids   # 分类缓存剩余时间
KEYS category:*      # 分类缓存 key（生产数据多时慎用 KEYS）
```

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

如果内存紧张，先 `docker compose stop ai-service`，客服会退化为本地规则 + 人工，不影响小程序主链路。

## 5. 验证

```bash
# 网关直连
curl http://127.0.0.1:8080/api/products/1

# Python 导购机器人（经网关；能吐 data: 行即正常）
curl -N http://127.0.0.1:8080/api/agent/chat/stream \
  -H 'Content-Type: application/json' \
  -d '{"message":"你好","sessionId":"deploy-check"}'

# Nacos 控制台（只建议内网/限制 IP 访问）
# http://服务器IP:18080
```

Nacos 控制台里应该能看到 8 个服务实例（Python `ai-service` 不注册 Nacos，由网关按容器名直连）。

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
- 真机测试登录、商品、下单、支付、AI 导购

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
- [ ] `SILICONFLOW_API_KEY` 是真实 key（Python `ai-service` 使用）
- [ ] `.env` 有 `MYSQL_HOST` / `MYSQL_PORT` / `MYSQL_DATABASE` / `SHOP_ID`，`ai-service` 能读到商品
- [ ] `docker logs ai-service` 无报错；网关 `/api/agent/chat/stream` 能逐块返回
- [ ] `WECHAT_SECRET` 正确
- [ ] `DB_URL` 指向正确的库，且库里有表和数据
- [ ] `/www/java/upload` 存在且可写，且已上传现有图片（`image/upload`、`body`、`category`、`swiper`、`share`、`QRcode.webp`、`store-logo.webp` 等）
- [ ] 已上传压缩图目录 `/www/java/upload/image/opt/`（`category` 200px、`upload` 1024px、`body` 1080px）；`WebMvcConfig` 优先读 opt，缺失时回落原图
- [ ] 图片/分类更新后已清 Cloudflare 缓存（`/image/category/*` 曾缓存 404，不清会继续 404，最长 4 小时）
- [ ] 时区是 `Asia/Shanghai`（Dockerfile 已带）
- [ ] 只放行 22/80/443；Nacos 控制台限制来源 IP
- [ ] 3306 不暴露公网；Redis 容器没有 `ports` 映射
- [ ] `REDIS_HOST=redis`、`SPRING_DATA_REDIS_PASSWORD` 已填；`docker exec redis redis-cli -a '密码' ping` 返回 PONG
- [ ] `docker compose ps` 全部 Up
- [ ] `https://api.jiemids.com/api/products/1` 能返回商品
- [ ] `https://api.jiemids.com/api/home?limit=10` 一次返回 `categories` + `recommend`
- [ ] `curl -o /dev/null -w "%{time_total}s\n" http://127.0.0.1:8080/api/categories` 在毫秒级（说明 Redis 命中，没走降级）

## 11. 镜像仓库发布（可选：腾讯云 TCR 个人版）

日常发布只用本地一条命令，服务器不再 build，并支持一键回滚。

### 首次配置

1. 腾讯云控制台 → 容器镜像服务 TCR → 个人版，创建命名空间（如 `jiemi-ecommerce`），为 8 个 Java 服务和 1 个 Python 服务各建一个仓库（共 9 个）：
   `auth-service`、`shop-service`、`product-service`、`order-service`、`aftersale-service`、`user-service`、`file-service`、`gateway`、`ai-service`。
2. TCR 控制台 → 访问凭证。个人版通常只有一个读写凭证；如果能单独创建只读凭证，服务器用只读的。
3. 本地登录并首次发布（`docker login` 交互输入密码，不要写进脚本）：

   ```bash
   docker login ccr.ccs.tencentyun.com -u '<账号ID>'
   export REGISTRY=ccr.ccs.tencentyun.com/jiemi-ecommerce
   export DEPLOY_HOST=ecommerce   # ~/.ssh/config 里的别名
   ./deploy/release.sh all
   ```

4. 服务器登录仓库（`docker login` 是按 Linux 用户保存的，执行部署的 SSH 用户必须登录过）：

   ```bash
   docker login ccr.ccs.tencentyun.com -u '<账号ID>'
   ```

5. 把新的 compose 同步到服务器（只需一次）：

   ```bash
   scp deploy/docker-compose.yml <用户>@<服务器>:/www/java/docker-compose.yml
   ```

6. 服务器 `.env` 补两行（手动执行 compose 时需要；`release.sh` 会自动通过环境变量传入）：

   ```
   REGISTRY=ccr.ccs.tencentyun.com/jiemi-ecommerce
   TAG=20261028-abc1234
   ```

7. 首次切换到镜像方式（服务器）：

   ```bash
   cd /www/java
   docker compose pull
   docker compose up -d --no-build
   ```

### 日常发布

```bash
./deploy/release.sh order-service                 # 改了一个服务
./deploy/release.sh order-service shop-service    # 改了多个
./deploy/release.sh all                           # 全部（8 个 Java + ai-service）
./deploy/release.sh --no-ai all                   # 不发布 Python ai-service
./deploy/release.sh --no-deploy order-service     # 只构建推送，不部署
./deploy/release.sh --dry-run all                 # 只看会执行什么
```

脚本流程：Maven 打包 → `docker build`（context 用 `<服务>/target`）→ `docker push` →
SSH 到服务器 `docker compose pull` / `up -d --no-build` → 打印容器状态。

### 回滚

```bash
./deploy/release.sh --rollback 20261027-abc1234 order-service
```

### 注意

- compose 里每个服务同时有 `image:` 和 `build:`；服务器部署必须带 `--no-build`，否则可能触发本地构建。
- 未配置 `REGISTRY` 时镜像名默认是 `local/...`，只适合服务器本地 `docker compose build`；走 TCR 必须配置 `REGISTRY`。
- `ai-service` 是 Python 服务，`release.sh` 会用 `../recommender-python/Dockerfile` 构建；目录不同时设置 `AI_SERVICE_DIR`。
- 镜像仓库不可用时，回退到第 1 节的 jar 上传方式。
- 建议在 TCR 配置镜像保留策略；服务器定期执行 `docker image prune -f`。
- 本机是 ARM 架构 Windows 时，构建 Linux/amd64 镜像需设置 `PLATFORM=linux/amd64`。

## 12. 后台管理端发布（www.jiemids.com）

一条命令完成构建 + 上传 + 替换：

```bash
./deploy/release-web.sh
```

- 站点目录默认 `/www/wwwroot/jiemids.com`，SSH 别名默认 `ecommerce`；可用 `WEB_ROOT`、`DEPLOY_HOST` 覆盖
- 前端目录默认 `../e-commerce`（可用 `WEB_DIR` 覆盖），脚本先执行 `npm run build`
- 上传后在服务器用 `rsync -a --delete` 同步 `index.html` / `assets` / `image` / `region`，
  保留 `.env`、`.htaccess`、`.user.ini`、`.well-known`、`public`
- 每次发布前用 `rsync --link-dest` 做硬链接快照到 `jiemids.com.bak.<时间戳>`（排除不可变的 `.user.ini`），默认保留 3 份
- 回滚：`./deploy/release-web.sh --rollback`（同步最近一份备份，不重新构建）
- 跳过构建：`--no-build`；只打印命令：`--dry-run`
- 静态文件不需要 reload Nginx；`assets` 文件名带 hash，不会命中旧缓存；若 `index.html` 被 Cloudflare 缓存，到控制台清一次缓存

前置条件：本机能 `ssh ecommerce true` 免密登录，且 `../e-commerce/node_modules` 已安装。
