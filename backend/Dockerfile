# Stage 1: Build
FROM maven:3.9-eclipse-temurin-17 AS builder
WORKDIR /build

# Copy POM files first for dependency caching
COPY pom.xml .
COPY ai-code-review-common/pom.xml ai-code-review-common/
COPY ai-code-review-repository/pom.xml ai-code-review-repository/
COPY ai-code-review-service/pom.xml ai-code-review-service/
COPY ai-code-review-integration/pom.xml ai-code-review-integration/
COPY ai-code-review-worker/pom.xml ai-code-review-worker/
COPY ai-code-review-api/pom.xml ai-code-review-api/

# Download dependencies (cached unless POMs change)
RUN mvn dependency:go-offline -B

# Copy source code and build
COPY . .
RUN mvn clean package -DskipTests -B

# Stage 2: Runtime
FROM eclipse-temurin:17-jre-alpine
WORKDIR /app

ENV SPRING_PROFILES_ACTIVE=docker

COPY --from=builder /build/ai-code-review-api/target/ai-code-review-api-*.jar app.jar

EXPOSE 8080

ENTRYPOINT ["java", "-jar", "app.jar"]
