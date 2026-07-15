# ---- build ----
FROM maven:3.9-eclipse-temurin-21 AS build
WORKDIR /app
COPY pom.xml .
RUN mvn -q dependency:go-offline
COPY src ./src
RUN mvn -q package -DskipTests

# ---- runtime ----
FROM eclipse-temurin:21-jre-alpine
RUN addgroup -S orion && adduser -S orion -G orion
WORKDIR /app
COPY --from=build /app/target/*.jar app.jar
USER orion
EXPOSE 8080
ENTRYPOINT ["java", "-XX:MaxRAMPercentage=75.0", "-jar", "app.jar"]
