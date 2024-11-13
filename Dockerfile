FROM openjdk:17
ENV TZ=Asia/Seoul
CMD ["./gradlew", "clean", "build"]
COPY ./build/libs/vehicle-placement-system-0.0.1-SNAPSHOT.jar /app.jar
ENTRYPOINT ["java", "-Dspring.profiles.active=prod", "-Duser.timezone=Asia/Seoul", "-jar","/app.jar"]