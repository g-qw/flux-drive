# 构建容器命令，在父项目的根目录运行：docker build -t gateway -f gateway/Dockerfile .

# 构建阶段
FROM maven:3.9.6-eclipse-temurin-21-alpine AS builder

WORKDIR /app

# 复制 Maven 配置
COPY settings.xml /root/.m2/settings.xml

# 复制父项目 pom.xml
COPY pom.xml .

# 复制 gateway 的 pom.xml
COPY gateway/pom.xml gateway/

# 解析 gateway 的依赖
RUN mvn dependency:resolve -B -f gateway/pom.xml

# 复制 gateway 源码并打包
COPY gateway/src gateway/src/
RUN mvn clean package -DskipTests -B -f gateway/pom.xml

# 运行阶段
FROM eclipse-temurin:21-jre-alpine AS runtime

WORKDIR /app
COPY --from=builder /app/gateway/target/gateway-*.jar app.jar

ENTRYPOINT ["java", \
    "-Djava.security.egd=file:/dev/./urandom", \
    "-Dspring.profiles.active=prod", \
    "-jar", "app.jar"]

EXPOSE 75

HEALTHCHECK --interval=30s \
           --timeout=10s \
           --start-period=60s \
           --retries=3 \
           CMD wget -qO- http://127.0.0.1:75/actuator/health > /dev/null || exit 1