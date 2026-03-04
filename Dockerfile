# Build stage
FROM eclipse-temurin:21-jdk-alpine AS builder
WORKDIR /app

COPY mvnw .
COPY .mvn .mvn
COPY pom.xml .
# Make mvnw executable (Windows doesn't preserve execute bit)
RUN chmod +x mvnw
# Download dependencies (cache layer)
RUN ./mvnw dependency:go-offline -B

COPY src src
RUN ./mvnw package -DskipTests -B

# Runtime stage
FROM eclipse-temurin:21-jre-alpine
WORKDIR /app

RUN addgroup -g 1000 app && adduser -u 1000 -G app -D app
USER app

COPY --from=builder /app/target/producer-0.0.1-SNAPSHOT.jar app.jar

# Tune for containers and high throughput
ENV JAVA_OPTS="-XX:+UseContainerSupport -XX:MaxRAMPercentage=75.0 -XX:+UseG1GC"
# Enable mock-testing profile (e.g. mock REST API for devices)
ENV SPRING_PROFILES_ACTIVE=mock-testing,default
ENTRYPOINT ["sh", "-c", "java $JAVA_OPTS -jar app.jar"]
