# ---------------------------------------------------------------------------
# Stage 1: build the Vue 3 frontend
#
# The frontend output lands in src/main/resources/static, so it must be built
# BEFORE the Maven stage packages the jar.
# ---------------------------------------------------------------------------
FROM node:22-alpine AS frontend
WORKDIR /fe

# Copy manifests first so dependency installation is cached independently of sources.
COPY frontend/package.json frontend/package-lock.json* ./

# --ignore-scripts is safe here: vite's esbuild binary ships via
# optionalDependencies rather than a postinstall download.
RUN npm install --no-audit --no-fund --ignore-scripts

COPY frontend/ ./

# Build into /static-out rather than the configured ../src/... path, because the
# backend sources are not present in this stage.
RUN npx vite build --outDir /static-out --emptyOutDir


# ---------------------------------------------------------------------------
# Stage 2: build the executable jar
# ---------------------------------------------------------------------------
FROM maven:3.9-eclipse-temurin-21 AS backend
WORKDIR /build

COPY pom.xml .

COPY src ./src

# Drop in the prebuilt frontend (this also provides the Vue index.html).
COPY --from=frontend /static-out/ ./src/main/resources/static/

# Build the Spring Boot executable jar.
# Maven will download the required dependencies during the build.
RUN mvn -B -q clean package -DskipTests


# ---------------------------------------------------------------------------
# Stage 3: minimal runtime
# ---------------------------------------------------------------------------
FROM eclipse-temurin:21-jre-alpine
WORKDIR /app

# Run as a non-root user.
RUN addgroup -S codebox && adduser -S codebox -G codebox

COPY --from=backend /build/target/codebox.jar app.jar

RUN chown -R codebox:codebox /app

USER codebox

EXPOSE 8080

# Container-aware memory sizing
ENV JAVA_OPTS="-XX:MaxRAMPercentage=75 -Djava.security.egd=file:/dev/./urandom"

HEALTHCHECK --interval=15s --timeout=5s --start-period=40s --retries=5 \
  CMD wget -qO- http://localhost:8080/api/health || exit 1

ENTRYPOINT ["sh", "-c", "exec java $JAVA_OPTS -jar app.jar"]