# Build stage
FROM gradle:8.7-jdk-21 AS build
WORKDIR /workspace
COPY ../../ .
RUN gradle :services:control-service:bootJar --no-daemon

# Runtime stage
FROM eclipse-temurin:21-jre
WORKDIR /app
COPY --from=build /workspace/services/control-service/build/libs/*.jar /app/app.jar
EXPOSE 8085
ENTRYPOINT ["java","-XX:MaxRAMPercentage=75.0","-jar","/app/app.jar"]
