FROM maven:3.9.9-eclipse-temurin-17 AS build
WORKDIR /app
COPY pom.xml ./
COPY src ./src
RUN mvn -B -DskipTests package

FROM eclipse-temurin:17.0.15_6-jre-alpine
RUN addgroup -S sicost && adduser -S sicost -G sicost && apk add --no-cache wget
WORKDIR /app
COPY --from=build --chown=sicost:sicost /app/target/si-cost.jar ./app.jar
USER sicost
EXPOSE 8080
HEALTHCHECK --interval=15s --timeout=5s --start-period=60s CMD wget -qO- http://127.0.0.1:8080/api/health || exit 1
ENTRYPOINT ["java", "-jar", "/app/app.jar"]
