FROM eclipse-temurin:25-jdk AS builder
WORKDIR /app
COPY . .
RUN ./mvnw -B -DskipTests package

FROM eclipse-temurin:25-jre
WORKDIR /opt/app
COPY --from=builder /app/target/*.jar app.jar
CMD ["java", "-jar", "app.jar"]
