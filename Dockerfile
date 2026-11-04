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
# 힙 최대 512MB. Railway는 사용량 과금이고 컨테이너 한도가 플랜 최대치로 보여서 비율로 잡으면 힙이 수 GB까지 커진다
# 바꾸려면 Railway 변수 JAVA_TOOL_OPTIONS (예: -Xmx1g). 포트는 Railway가 넘기는 PORT를 공용 설정이 쓴다
ENV JAVA_TOOL_OPTIONS="-Xmx512m"
ENTRYPOINT ["java", "-jar", "/app/app.jar"]
