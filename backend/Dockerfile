FROM maven:3.9.16-eclipse-temurin-17 AS build

WORKDIR /app

# Maven 3.9.16 uses Resolver 1.9.x: limit parallel downloads and back off on
# HTTP 429/503. Retry-After is respected; longer waits fail instead of hanging.
# Keep .m2 in the dependency layer so successful downloads remain cacheable.
ENV MAVEN_OPTS="-Dmaven.artifact.threads=2 -Daether.connector.http.retryHandler.count=2 -Daether.connector.http.retryHandler.interval=30000 -Daether.connector.http.retryHandler.intervalMax=120000"

COPY pom.xml .

RUN mvn -B --no-transfer-progress dependency:go-offline

COPY src ./src

RUN mvn -B --no-transfer-progress clean package -DskipTests

FROM eclipse-temurin:17-jre

WORKDIR /app

COPY --from=build /app/target/*.jar app.jar

EXPOSE 8080

ENTRYPOINT ["sh", "-c", "java -jar app.jar --server.port=${PORT:-10000} --server.address=0.0.0.0"]
