# Stage 1: Build the application
FROM maven:3.9.11-amazoncorretto-24-alpine AS builder

WORKDIR /build
COPY ./src /build/src
COPY ./pom.xml /build
RUN mvn --no-transfer-progress --batch-mode --update-snapshots --also-make package

# Stage 2: Create a smaller runtime image, but have 'curl' and 'jq' available
FROM amazoncorretto:24-alpine
RUN apk upgrade --no-cache && apk add curl jq

WORKDIR /app/broccoli
COPY --from=builder /build/target/broccoli-*.jar broccoli.jar
COPY ./k8s/broccoli-server/entrypoint.sh entrypoint.sh
COPY ./config.yml config.yml
ENV BR_CONFIG_FILE=config.yml
EXPOSE 8080
CMD ["./entrypoint.sh"]
