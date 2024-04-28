FROM openjdk:17
CMD ["./gradlew", "clean", "build"]
COPY ./build/libs/vehicle-placement-system-0.0.1-SNAPSHOT.jar /app.jar
ENTRYPOINT ["java", "-Dspring.profiles.active=prod", "-jar","/app.jar"]