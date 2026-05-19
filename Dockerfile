# Stage 1: Build the application
FROM maven:3.9.6-eclipse-temurin-21-alpine AS build
WORKDIR /app

# Copy only the POM file first to cache dependencies
COPY pom.xml .
RUN mvn dependency:go-offline -B

# Copy the source code and build the application
COPY src ./src
RUN mvn package -DskipTests -B

# Stage 2: Create the minimal runtime image
FROM eclipse-temurin:21-jre-alpine AS runtime
WORKDIR /app

# Create a non-root user and group for security
RUN addgroup -g 1001 -S springgroup && \
    adduser -S springuser -u 1001 -G springgroup

# Copy the built JAR file from the build stage
COPY --from=build --chown=springuser:springgroup /app/target/UrlShortenerService-1.0-SNAPSHOT.jar app.jar

# Run as non-root user
USER 1001

# Expose the application port
EXPOSE 8080

# Configure a health check relying on Spring Boot Actuator
HEALTHCHECK --interval=30s --timeout=10s --start-period=30s --retries=3 \
  CMD wget -qO- http://localhost:8080/actuator/health/ || exit 1

# Define the entrypoint
ENTRYPOINT ["java", "-jar", "app.jar"]
