# Build stage
FROM gradle:8.7-jdk-21 AS build
WORKDIR /workspace
COPY ../../ .
RUN gradle :services:gateway-service:bootJar --no-daemon

# Runtime stage
FROM eclipse-temurin:21-jre
WORKDIR /app
COPY --from=build /workspace/services/gateway-service/build/libs/*.jar /app/app.jar
EXPOSE 8080
ENTRYPOINT ["java","-XX:MaxRAMPercentage=75.0","-jar","/app/app.jar"]
