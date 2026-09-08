# Stage 1: Build the application using Eclipse Temurin JDK 21
FROM eclipse-temurin:21-jdk-alpine AS builder
WORKDIR /build

# Copy Maven wrapper & POM to leverage Docker layer caching
COPY .mvn/ .mvn/
COPY mvnw pom.xml ./
RUN chmod +x mvnw && ./mvnw dependency:go-offline -B

# Copy source code and build production package
COPY src ./src
RUN ./mvnw clean package -DskipTests -B

# Stage 2: Minimal, secure runtime container
FROM eclipse-temurin:21-jre-alpine
WORKDIR /app

# Add unprivileged user for security
RUN addgroup -S queuecut && adduser -S queuecut -G queuecut

# Copy built JAR from builder stage
COPY --from=builder /build/target/*.jar app.jar
RUN chown -R queuecut:queuecut /app

USER queuecut:queuecut
EXPOSE 8080

# Production-tuned JVM flags
ENTRYPOINT ["java", \
    "-XX:+UseParallelGC", \
    "-XX:MaxRAMPercentage=75.0", \
    "-Djava.security.egd=file:/dev/./urandom", \
    "-jar", "app.jar"]
