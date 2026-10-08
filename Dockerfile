# ---- Stage 1: build the jar ----
FROM eclipse-temurin:21-jdk AS build
WORKDIR /workspace

# Gradle files first, so dependency downloads are cached between builds
COPY gradlew settings.gradle build.gradle ./
COPY gradle gradle
RUN chmod +x gradlew && ./gradlew dependencies --no-daemon > /dev/null

# Then the source, which changes far more often
COPY src src
RUN ./gradlew bootJar --no-daemon -x test

# ---- Stage 2: run it ----
FROM eclipse-temurin:21-jre
WORKDIR /app

RUN useradd --system --uid 1001 app
COPY --from=build /workspace/build/libs/*-SNAPSHOT.jar app.jar
USER app

EXPOSE 8080
ENTRYPOINT ["java", "-jar", "app.jar"]