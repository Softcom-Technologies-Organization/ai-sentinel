# syntax=docker/dockerfile:1

FROM maven:3.9.16-amazoncorretto-25 AS build

WORKDIR /workspace

COPY pii-reporting-api/pom.xml ./
COPY proto ./proto
RUN mvn -B dependency:go-offline

COPY pii-reporting-api/src ./src

RUN mvn -B clean package -DskipTests -Pdocker-build

FROM amazoncorretto:25 AS runtime

RUN curl -fsSL 'https://artifacts-cli.infisical.com/setup.rpm.sh' -o /tmp/infisical-setup.sh \
    && bash /tmp/infisical-setup.sh \
    && yum install -y infisical \
    && yum clean all \
    && rm -rf /var/cache/yum /tmp/infisical-setup.sh

WORKDIR /app

COPY --from=build /workspace/target/ai-sentinel-*.jar /app/app.jar

# Copy entrypoint script
COPY pii-reporting-api/docker-entrypoint.sh /usr/local/bin/
RUN chmod +x /usr/local/bin/docker-entrypoint.sh

EXPOSE 8080

ENTRYPOINT ["/usr/local/bin/docker-entrypoint.sh"]
CMD ["java", "-jar", "/app/app.jar"]
