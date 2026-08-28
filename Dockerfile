FROM maven:3.9.9-eclipse-temurin-17-alpine AS build
WORKDIR /workspace
COPY pom.xml .
COPY src ./src
RUN mvn -q -DskipTests package

FROM eclipse-temurin:17-jre-alpine
WORKDIR /app
RUN addgroup -S wavy && adduser -S -G wavy wavy
COPY --from=build --chown=wavy:wavy /workspace/target/*.jar app.jar
USER wavy
EXPOSE 8086
ENTRYPOINT ["java", "-jar", "/app/app.jar"]
