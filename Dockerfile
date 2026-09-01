# Runtime image for the feature-service JAR built by `./mvnw package` (backend + admin panel in one JAR).
# Build:  ./mvnw package && docker build -t feature-service:latest .
# Run:    docker run -p 11230:11230 -p 9091:9091 feature-service:latest          (dev, in-memory H2)
#         add -e SPRING_PROFILES_ACTIVE=kubernetes -e K_DB_URL=... for Postgres deployments
FROM eclipse-temurin:21-jre-noble

RUN useradd --system --uid 1001 appuser
USER appuser
WORKDIR /app

COPY target/feature-service-*.jar app.jar

# 11230/9091 = default profile, 8080/9090 = kubernetes profile
EXPOSE 11230 9091 8080 9090

ENTRYPOINT ["java", "-jar", "app.jar"]
