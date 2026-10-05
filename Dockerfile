# MembersSecurity (인증 서버 - 8086 포트)
FROM eclipse-temurin:17-jdk AS builder
WORKDIR /workspace

COPY gradlew gradlew
COPY gradle/ gradle/
COPY settings.gradle settings.gradle

COPY backend/EntityCom/ backend/EntityCom/
COPY backend/MembersSecurity/ backend/MembersSecurity/

# settings.gradle에 포함된 나머지 모듈 - 빌드 불필요하나 Gradle 설정 평가 시 디렉토리 필요
RUN mkdir -p backend/AgentProgram backend/ProgramLog && \
    printf 'plugins { id "java" }\n' > backend/AgentProgram/build.gradle && \
    printf 'plugins { id "java" }\n' > backend/ProgramLog/build.gradle

RUN chmod +x gradlew && ./gradlew :MembersSecurity:bootJar --no-daemon

FROM amazoncorretto:17-alpine-jdk
COPY --from=builder /workspace/backend/MembersSecurity/build/libs/*.jar app.jar
EXPOSE 8086
# -Dspring.config.location=classpath:/com/example/memberssecurity/ 이 경로에서만 yml을 찾아라하고 스프링에 알려주는 것이다.
ENTRYPOINT ["java", "-jar", "-Dspring.config.name=membersSecurity-application", "/app.jar"]
