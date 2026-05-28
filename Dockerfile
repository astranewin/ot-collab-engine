FROM eclipse-temurin:21-jre

WORKDIR /app

COPY target/real-time_collaborative_editor-0.0.1-SNAPSHOT.jar app.jar

EXPOSE 8080

LABEL authors="astranewin"

ENTRYPOINT ["java", \
            "-Dspring.datasource.url=${SPRING_DATASOURCE_URL}", \
            "-Dspring.datasource.username=${SPRING_DATASOURCE_USERNAME}", \
            "-Dspring.datasource.password=${SPRING_DATASOURCE_PASSWORD}", \
            "-Dcustom.jwt_secret=${JWT_SECRET}", \
            "-jar", "app.jar"]