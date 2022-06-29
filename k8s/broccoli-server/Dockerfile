FROM openjdk:18.0.1.1-slim-buster

WORKDIR /apps/broccoli
COPY ./target/broccoli-*.jar /apps/broccoli/broccoli.jar
COPY ./k8s/broccoli-server/entrypoint.sh entrypoint.sh
COPY ./config.yml config.yml
EXPOSE 8080
CMD ["./entrypoint.sh"]