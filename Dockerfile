# Multi-stage build for Spring Boot application
FROM maven:3.9.5-openjdk-17-slim AS build

# Set working directory
WORKDIR /app

# Copy pom.xml first for better caching
COPY pom.xml .

# Download dependencies
RUN mvn dependency:go-offline -B

# Copy source code
COPY src ./src

# Build the application
RUN mvn clean package -DskipTests

# Runtime stage
# Runtime stage
FROM amazoncorretto:17

# Install curl for health checks (Amazon Linux uses yum)
RUN yum install -y curl && yum clean all

# Create non-root user
RUN groupadd -r inventory && useradd -r -g inventory inventory

# Set working directory
WORKDIR /app

# Copy the jar file from build stage
COPY --from=build /app/target/inventory-service-*.jar app.jar

# Change ownership to non-root user
RUN chown inventory:inventory app.jar

# Switch to non-root user
USER inventory

# Expose port
EXPOSE 8081

# Health check
HEALTHCHECK --interval=30s --timeout=3s --start-period=60s --retries=3 \
  CMD curl -f http://localhost:8081/actuator/health || exit 1

# JVM options for production
ENV JAVA_OPTS="-Xms512m -Xmx1024m -XX:+UseG1GC -XX:+UseStringDeduplication -XX:+OptimizeStringConcat"

# Run the application
ENTRYPOINT ["sh", "-c", "java $JAVA_OPTS -jar app.jar"]
