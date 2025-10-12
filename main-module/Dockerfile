FROM eclipse-temurin:23-jdk

WORKDIR /app

# Install curl for health checks and create required directories
RUN apt-get update && \
    apt-get install -y curl && \
    rm -rf /var/lib/apt/lists/* && \
    mkdir -p /app/logs

# Copy the built jar file (use the plain jar which contains all dependencies)
COPY build/libs/*-plain.jar app.jar

# Create non-root user for security
RUN groupadd -r vectordb && useradd -r -g vectordb vectordb && \
    chown -R vectordb:vectordb /app
USER vectordb

# Expose port
EXPOSE 8080

# Health check
HEALTHCHECK --interval=30s --timeout=10s --start-period=60s --retries=3 \
  CMD curl -f http://localhost:8080/api/v1/health || exit 1

# Set JVM options for production
ENV JAVA_OPTS="-Xms512m -Xmx1g -XX:+UseG1GC -XX:+UseContainerSupport -XX:MaxRAMPercentage=75.0 -XX:+ExitOnOutOfMemoryError -Djava.security.egd=file:/dev/./urandom"

# Run the application
ENTRYPOINT ["sh", "-c", "java $JAVA_OPTS -jar app.jar"]
