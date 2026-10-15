FROM maven:3.9-eclipse-temurin-21 AS build
WORKDIR /app
COPY pom.xml .
RUN mvn dependency:go-offline -B
COPY src ./src
RUN mvn verify -B
COPY docker/Healthcheck.java /tmp/Healthcheck.java
RUN javac -d /app /tmp/Healthcheck.java
FROM eclipse-temurin:21-jre-alpine
RUN addgroup -S ankidsa && adduser -S ankidsa -G ankidsa
WORKDIR /app
COPY --from=build /app/target/*.jar app.jar
COPY --from=build /app/Healthcheck.class Healthcheck.class
ENV JAVA_TOOL_OPTIONS="-XX:InitialRAMPercentage=10 -XX:MaxRAMPercentage=50 -XX:+ExitOnOutOfMemoryError"
USER ankidsa
EXPOSE 8081
HEALTHCHECK --interval=30s --timeout=10s --start-period=120s --retries=3 CMD java -Xms4m -Xmx16m -cp /app Healthcheck
ENTRYPOINT ["java","-jar","app.jar"]
