# 所有服务通用的 Dockerfile：每个服务目录放一份这个文件 + 一个 jar
# gateway / product / order / ai 在 docker-compose.yml 里用 JAVA_OPTS 覆盖为 384m
FROM eclipse-temurin:17-jre-jammy

ENV TZ=Asia/Shanghai
WORKDIR /app

COPY *.jar app.jar

# 小服务默认 256m 堆；-Duser.timezone 与 TZ 一起保证 LocalDateTime.now() 是北京时间
ENV JAVA_OPTS="-Xms96m -Xmx256m -XX:MaxMetaspaceSize=128m -XX:ReservedCodeCacheSize=64m -Xss256k -XX:+UseSerialGC -Duser.timezone=Asia/Shanghai"

# exec 让 java 成为 PID 1，docker stop 能正常收到信号
ENTRYPOINT ["sh", "-c", "exec java $JAVA_OPTS -jar app.jar"]
