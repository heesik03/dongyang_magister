FROM eclipse-temurin:21-jdk-noble AS build
WORKDIR /build
COPY gradlew build.gradle settings.gradle ./
COPY gradle/ gradle/
COPY src/main/ src/main/
RUN bash gradlew --no-daemon bootJar

FROM eclipse-temurin:21-jre-noble
RUN apt-get update \
    && apt-get install -y --no-install-recommends fonts-nanum \
    && rm -rf /var/lib/apt/lists/*
WORKDIR /app
COPY --from=build /build/build/libs/*.jar /app/magister.jar
ENV SPRING_PROFILES_ACTIVE=prod
EXPOSE 8080
USER 10001:10001
ENTRYPOINT ["java", "-XX:-UseBASE64Intrinsics", "-jar", "/app/magister.jar"]
