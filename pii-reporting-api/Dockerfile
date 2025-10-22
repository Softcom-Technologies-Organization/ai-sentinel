# syntax=docker/dockerfile:1

FROM maven:4.0.0-rc-4-amazoncorretto-24 AS build

WORKDIR /workspace

COPY pii-reporting-api/pom.xml ./
COPY proto ./proto
RUN mvn -B dependency:go-offline

COPY pii-reporting-api/src ./src

RUN mvn -B clean package -DskipTests -Pdocker-build

FROM amazoncorretto:24 AS runtime

WORKDIR /app

COPY --from=build /workspace/target/*-SNAPSHOT.jar /app/app.jar

EXPOSE 8080

ENTRYPOINT ["java", "-jar", "/app/app.jar"]
