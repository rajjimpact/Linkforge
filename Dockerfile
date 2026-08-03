# ====================================================
# LinkForge — Multi-Stage Dockerfile
# Stage 1: Maven build
# Stage 2: JRE 21 slim runtime
# ====================================================

# Stage 1: Build
FROM maven:3.9.9-eclipse-temurin-21 AS builder

WORKDIR /app

# Cache dependencies separately (improves layer caching)
COPY pom.xml .
RUN mvn dependency:go-offline -B

# Copy source and build
COPY src ./src
RUN mvn package -DskipTests -B --no-transfer-progress

# Stage 2: Runtime
FROM eclipse-temurin:21-jre-jammy AS runtime

# Create non-root user for security
RUN groupadd -r linkforge && useradd -r -g linkforge linkforge

WORKDIR /app

# Create upload directories
RUN mkdir -p uploads/avatars uploads/qr-codes logs && \
    chown -R linkforge:linkforge /app

# Copy the fat JAR
COPY --from=builder --chown=linkforge:linkforge /app/target/linkforge-*.jar app.jar

# Switch to non-root user
USER linkforge

# Expose port
EXPOSE 8080

# Health check
HEALTHCHECK --interval=30s --timeout=10s --start-period=60s --retries=3 \
  CMD wget -q --spider http://localhost:8080/actuator/health || exit 1

# JVM tuning for containers
ENV JAVA_OPTS="-XX:+UseContainerSupport \
               -XX:MaxRAMPercentage=75.0 \
               -XX:+UseG1GC \
               -XX:+ExitOnOutOfMemoryError \
               --enable-preview \
               -Djava.security.egd=file:/dev/./urandom"

ENTRYPOINT ["sh", "-c", "java $JAVA_OPTS -jar app.jar"]
