# ── Build stage ──────────────────────────────────────────────────────────────
FROM clojure:temurin-21-lein AS builder

WORKDIR /app

# Resolve deps first (cached layer)
COPY project.clj .
RUN lein deps

# Build uberjar
COPY . .
RUN lein uberjar

# ── Runtime stage ─────────────────────────────────────────────────────────────
FROM eclipse-temurin:21-jre-alpine

WORKDIR /app

# SQLite DB lives here; mount a volume to persist data across restarts
RUN mkdir -p .data

COPY --from=builder /app/target/uberjar/pearslcore-test-0.1.0-SNAPSHOT-standalone.jar app.jar

EXPOSE 3000

# Mount .data as a volume so the SQLite file survives container restarts
VOLUME ["/app/.data"]

ENTRYPOINT ["java", "-jar", "app.jar"]
