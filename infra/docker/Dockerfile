# ===== 1단계: 빌드 =====
FROM eclipse-temurin:21-jdk AS builder
WORKDIR /workspace

# 의존성 레이어 캐싱: 소스보다 먼저 빌드 스크립트만 복사
COPY gradlew settings.gradle build.gradle ./
COPY gradle gradle
RUN chmod +x gradlew && ./gradlew --no-daemon dependencies || true

COPY src src
RUN ./gradlew --no-daemon clean bootJar -x test

# ===== 2단계: 실행 =====
FROM eclipse-temurin:21-jre
WORKDIR /app

ENV TZ=Asia/Seoul \
    JAVA_OPTS="-XX:MaxRAMPercentage=75 -Duser.timezone=Asia/Seoul"

RUN useradd --system --create-home --shell /usr/sbin/nologin jolly
COPY --from=builder /workspace/build/libs/*.jar app.jar
RUN chown jolly:jolly app.jar
USER jolly

EXPOSE 8080
ENTRYPOINT ["sh", "-c", "exec java $JAVA_OPTS -jar /app/app.jar"]
