# syntax=docker/dockerfile:1

FROM maven:4.0.0-rc-4-amazoncorretto-24 AS build

WORKDIR /workspace

COPY pii-reporting-api/pom.xml ./
COPY proto ./proto
RUN mvn -B dependency:go-offline

COPY pii-reporting-api/src ./src

RUN mvn -B clean package -DskipTests -Pdocker-build

FROM amazoncorretto:24 AS runtime

# Install Infisical CLI
# Note: curl-minimal and bash are already installed in amazoncorretto:24 base image
RUN curl -1sLf 'https://dl.cloudsmith.io/public/infisical/infisical-cli/setup.rpm.sh' | bash \
    && yum install -y infisical \
    && yum clean all \
    && rm -rf /var/cache/yum

WORKDIR /app

COPY --from=build /workspace/target/*-SNAPSHOT.jar /app/app.jar

# Copy entrypoint script
COPY pii-reporting-api/docker-entrypoint.sh /usr/local/bin/
RUN chmod +x /usr/local/bin/docker-entrypoint.sh

EXPOSE 8080

ENTRYPOINT ["/usr/local/bin/docker-entrypoint.sh"]
CMD ["java", "-jar", "/app/app.jar"]
