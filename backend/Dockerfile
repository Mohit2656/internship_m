FROM maven:3.9.16-eclipse-temurin-17 AS build

WORKDIR /app

# Limit parallel downloads. The wrapper owns retries; disable nested HTTP
# retries so four whole-command attempts remain predictable.
# Keep .m2 in the dependency layer so successful downloads remain cacheable.
ENV MAVEN_OPTS="-Dmaven.artifact.threads=2 -Daether.connector.http.retryHandler.count=0"

COPY scripts/maven-with-retry.sh /usr/local/bin/maven-with-retry.sh

COPY pom.xml .

RUN sh /usr/local/bin/maven-with-retry.sh dependency:go-offline

COPY src ./src

RUN sh /usr/local/bin/maven-with-retry.sh clean package -DskipTests

FROM eclipse-temurin:17-jre

WORKDIR /app

COPY --from=build /app/target/*.jar app.jar

EXPOSE 8080

ENTRYPOINT ["sh", "-c", "java -jar app.jar --server.port=${PORT:-10000} --server.address=0.0.0.0"]
