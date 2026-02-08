# Multi-stage build for Spring Boot application
FROM maven:3.9-amazoncorretto-17 AS build
WORKDIR /app

# Copy the entire project context (parent pom and all modules)
COPY pom.xml .
COPY common ./common
COPY product-service ./product-service
COPY search-service ./search-service

# Build all modules
RUN mvn clean package -DskipTests

# Runtime stage
FROM amazoncorretto:17
WORKDIR /app
# Install curl for health checks (Amazon Linux uses yum) and shadow-utils for user management
RUN yum install -y curl shadow-utils && yum clean all

# Create non-root user
RUN groupadd -r appuser && useradd -r -g appuser appuser

# Define which module to package in this image
ARG MODULE_NAME=product-service
# Fail if MODULE_NAME is not set
RUN if [ -z "$MODULE_NAME" ]; then echo "MODULE_NAME is required" && exit 1; fi

# Copy the specific module's jar
COPY --from=build /app/${MODULE_NAME}/target/*.jar app.jar

# Change ownership and switch user
RUN chown appuser:appuser app.jar
USER appuser

EXPOSE 8081 8083

HEALTHCHECK --interval=30s --timeout=3s --start-period=60s --retries=3 \
  CMD curl -f http://localhost:8081/actuator/health || curl -f http://localhost:8083/actuator/health || exit 1

ENV JAVA_OPTS="-Xms512m -Xmx1024m -XX:+UseG1GC -XX:+UseStringDeduplication -XX:+OptimizeStringConcat"
ENTRYPOINT ["sh", "-c", "java $JAVA_OPTS -jar app.jar"]
