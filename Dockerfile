# 배포 이미지 (Railway가 이 파일로 빌드, ADR 0018). 로컬에서도 같은 이미지: docker build -t capsule .
# 테스트는 CI(verify)가 이미 돌리므로 여기서는 빌드만 한다

FROM eclipse-temurin:21-jdk AS build
WORKDIR /workspace
# 의존성 층을 먼저 받아 두면 코드만 바뀐 빌드는 캐시를 쓴다
COPY gradlew settings.gradle build.gradle ./
COPY gradle gradle
RUN chmod +x gradlew && ./gradlew --no-daemon dependencies > /dev/null
COPY src src
RUN ./gradlew --no-daemon bootJar && cp build/libs/*.jar app.jar

FROM eclipse-temurin:21-jre
WORKDIR /app
RUN useradd --system --uid 10001 capsule
COPY --from=build /workspace/app.jar app.jar
USER capsule
# 컨테이너 메모리 한도의 75%까지 힙으로. Railway가 PORT를 넘기면 dev-server · prod 프로필이 그 포트를 쓴다
ENTRYPOINT ["java", "-XX:MaxRAMPercentage=75", "-jar", "/app/app.jar"]
