# syntax=docker/dockerfile:1
FROM maven:3.10-eclipse-temurin-21-noble AS build
WORKDIR /workspace

COPY . .
RUN --mount=type=cache,target=/root/.m2 mvn --batch-mode --no-transfer-progress -Dmaven.test.skip=true package

FROM eclipse-temurin:21-jre-noble
WORKDIR /app

COPY --from=build /workspace/target/bulksms-mock-gateway-*.jar /app/gateway.jar

ENV SERVER_ADDRESS=0.0.0.0
EXPOSE 8080
# Render grants access to mounted secret files through group 1000.
USER 10001:1000

ENTRYPOINT ["java", "-jar", "/app/gateway.jar"]
