FROM maven:3.9-eclipse-temurin-17 AS build
WORKDIR /workspace
COPY backend/pom.xml backend/pom.xml
RUN mvn -f backend/pom.xml dependency:go-offline
COPY backend backend
RUN mvn -f backend/pom.xml package -DskipTests

FROM eclipse-temurin:17-jre
WORKDIR /app
COPY --from=build /workspace/backend/target/sentinel-ai-0.0.1-SNAPSHOT.jar app.jar
EXPOSE 8090
ENTRYPOINT ["java", "-jar", "/app/app.jar"]
