FROM maven:3.9-eclipse-temurin-21 AS build
WORKDIR /app
COPY pom.xml .
RUN mvn dependency:go-offline -B
COPY src ./src
RUN mvn verify -B
FROM eclipse-temurin:21-jre-alpine
RUN addgroup -S ankidsa && adduser -S ankidsa -G ankidsa
WORKDIR /app
COPY --from=build /app/target/*.jar app.jar
USER ankidsa
EXPOSE 8081
ENTRYPOINT ["java","-jar","app.jar"]
