# Multi-stage Dockerfile for LedgerLine Spring Boot Backend
FROM eclipse-temurin:21-jdk-jammy AS builder
WORKDIR /workspace

# Copy Maven wrapper and POM for dependency caching
COPY .mvn/ .mvn
COPY mvnw pom.xml ./
RUN chmod +x mvnw && ./mvnw dependency:go-offline -B

# Copy source code and build production artifact
COPY src ./src
RUN ./mvnw clean package -DskipTests -B

# Production Runtime Stage
FROM eclipse-temurin:21-jre-jammy
WORKDIR /app

# Run as non-privileged system user for container security
RUN groupadd -r ledger && useradd -r -g ledger ledger
USER ledger

# Copy the built JAR from builder stage
COPY --from=builder /workspace/target/*.jar app.jar

# Expose HTTP port
EXPOSE 8080

# Configure container runtime flags
ENTRYPOINT ["java", "-Djava.security.egd=file:/dev/./urandom", "-XX:+UseG1GC", "-jar", "app.jar"]

