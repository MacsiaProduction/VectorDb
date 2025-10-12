FROM eclipse-temurin:23-jdk

WORKDIR /app

# Install curl for health checks and RocksDB native dependencies
RUN apt-get update && \
    apt-get install -y curl libgflags-dev libsnappy-dev zlib1g-dev libbz2-dev liblz4-dev libzstd-dev && \
    rm -rf /var/lib/apt/lists/* && \
    mkdir -p /app/data /app/logs

# Copy the built jar file (use the plain jar which contains all dependencies)
COPY build/libs/*-plain.jar app.jar

# Create a volume mount point for persistent data
VOLUME ["/app/data"]

# Create non-root user for security
RUN groupadd -r vectordb && useradd -r -g vectordb vectordb && \
    chown -R vectordb:vectordb /app
USER vectordb

# Expose port
EXPOSE 8081

# Health check
HEALTHCHECK --interval=30s --timeout=10s --start-period=60s --retries=3 \
  CMD curl -f http://localhost:8081/api/v1/storage/health || exit 1

# Set JVM options for production with RocksDB optimizations
ENV JAVA_OPTS="-Xms1g -Xmx2g -XX:+UseG1GC -XX:+UseContainerSupport -XX:MaxRAMPercentage=75.0 -XX:+ExitOnOutOfMemoryError -Djava.security.egd=file:/dev/./urandom -Djava.library.path=/usr/lib/x86_64-linux-gnu"

# Run the application
ENTRYPOINT ["sh", "-c", "java $JAVA_OPTS -jar app.jar"]
