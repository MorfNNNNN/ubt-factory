# Build context: the shield/ directory.  docker build -f server/Dockerfile -t family-shield-server .
FROM gradle:8.14.3-jdk21 AS build
WORKDIR /src
COPY . .
ENV SHIELD_SERVER_ONLY=true
RUN gradle --no-daemon -q :server:test :server:fatJar

FROM eclipse-temurin:21-jre
RUN useradd --system --uid 10001 shield && mkdir -p /app && chown shield /app
COPY --from=build /src/server/build/libs/family-shield-server.jar /app/server.jar
USER shield
WORKDIR /app
EXPOSE 8080
HEALTHCHECK --interval=30s --timeout=5s CMD wget -qO- http://127.0.0.1:8080/health || exit 1
ENTRYPOINT ["java", "-XX:MaxRAMPercentage=75", "-jar", "/app/server.jar"]
