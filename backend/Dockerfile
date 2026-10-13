# syntax=docker/dockerfile:1
# Dockerfile đa giai đoạn cho backend Campus Hub Guard (Spring Boot 3.5, Java 21).
# Chạy được trên linux/amd64 và linux/arm64.

# ---------- Giai đoạn 1: build ----------
FROM eclipse-temurin:21-jdk AS build
WORKDIR /workspace

# Copy trước các tệp cấu hình Gradle để tận dụng cache lớp (layer) cho phần tải dependency.
COPY gradlew build.gradle settings.gradle ./
COPY gradle/ gradle/

# Chuẩn hóa xuống dòng (tránh lỗi khi tệp được checkout với CRLF trên Windows) và cấp quyền thực thi.
RUN sed -i 's/\r$//' gradlew && chmod +x gradlew

# Tải dependency trước; không làm hỏng build nếu bước này lỗi nhẹ.
RUN ./gradlew dependencies --no-daemon > /dev/null || true

# Copy mã nguồn sau cùng để chỉ build lại khi mã nguồn thay đổi.
COPY src/ src/
RUN ./gradlew bootJar --no-daemon -x test \
    && mkdir -p /workspace/dist \
    && cp "$(ls build/libs/*.jar | grep -v -- '-plain.jar' | head -n 1)" /workspace/dist/app.jar

# ---------- Giai đoạn 2: runtime ----------
FROM eclipse-temurin:21-jre
WORKDIR /app

# curl phục vụ HEALTHCHECK; tạo người dùng không phải root để chạy ứng dụng.
RUN apt-get update \
    && apt-get install -y --no-install-recommends curl \
    && rm -rf /var/lib/apt/lists/* \
    && groupadd --system app \
    && useradd --system --gid app --home-dir /app --shell /usr/sbin/nologin app

COPY --from=build --chown=app:app /workspace/dist/app.jar /app/app.jar

USER app
EXPOSE 8080

# Giới hạn bộ nhớ heap theo bộ nhớ của container; có thể ghi đè qua biến JAVA_OPTS.
ENV JAVA_OPTS="-XX:MaxRAMPercentage=75"

HEALTHCHECK --interval=15s --timeout=5s --start-period=60s --retries=10 \
    CMD curl -fsS http://localhost:8080/actuator/health || exit 1

ENTRYPOINT ["sh", "-c", "exec java $JAVA_OPTS -jar /app/app.jar"]
