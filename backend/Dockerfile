# 多阶段构建：后端 Spring Boot 应用
FROM maven:3.9-eclipse-temurin-21-alpine AS build
WORKDIR /app
COPY pom.xml .
COPY config ./config
COPY ai-core ./ai-core
COPY app ./app
RUN mvn package -DskipTests -B

FROM eclipse-temurin:25-jre-alpine
ARG APP_REVISION=unknown
ARG APP_WORKTREE_STATE=unknown
ARG APP_SOURCE_FINGERPRINT=unknown
WORKDIR /app
COPY --from=build /app/app/target/*.jar app.jar
LABEL org.opencontainers.image.revision=$APP_REVISION \
      com.learnplatform.worktree-state=$APP_WORKTREE_STATE \
      com.learnplatform.source-fingerprint=$APP_SOURCE_FINGERPRINT
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "app.jar"]
