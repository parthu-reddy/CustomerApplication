FROM eclipse-temurin:17-jre-jammy
WORKDIR /app

# Non-root user setup for security
RUN useradd -m spring
USER spring

# Copy pre-compiled JAR from the target directory
COPY CustomerApplication/target/*.jar app.jar

ENV JAVA_OPTS="-XX:MaxRAMPercentage=75.0 -XX:+UseG1GC"

EXPOSE 8092
ENTRYPOINT ["sh", "-c", "java $JAVA_OPTS -jar app.jar"]
