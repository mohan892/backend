# Stage 1: Build Java 17 Spring Boot Jar with Maven
FROM eclipse-temurin:17-jdk-alpine AS build
WORKDIR /app

# Copy all repository files
COPY . .

# Make Maven wrapper executable and build executable JAR
RUN chmod +x mvnw && ./mvnw clean package -DskipTests

# Stage 2: Minimal Production JRE Runtime
FROM eclipse-temurin:17-jre-alpine
WORKDIR /app

# Copy built JAR from build stage
COPY --from=build /app/target/*.jar app.jar

# Expose Render PORT
ENV PORT=8080
EXPOSE 8080

# Run Spring Boot Application
ENTRYPOINT ["java", "-jar", "app.jar"]
