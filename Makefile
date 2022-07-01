NAME := broccoli
VER  := $(shell mvn help:evaluate -Dexpression=project.version -q -DforceStdout)
JAR  := target/$(NAME)-$(VER).jar
TAG  := $(NAME):$(VER)

all: help

$(JAR): $(shell find src -type f) pom.xml
	mvn package

.PHONY: build
build: $(JAR)

.PHONY: run-server
run-server: $(JAR)
	java -jar $(JAR) server config.yml

.PHONY: clean
clean:
	rm -rf .make
	mvn clean

.PHONY: version-update
version-update:
	mvn versions:set && mvn versions:commit

.make:
	mkdir -p .make

.make/.push: .make $(JAR) k8s/broccoli-server/Dockerfile
	docker build --tag $(TAG) --platform=linux/amd64 --file k8s/broccoli-server/Dockerfile .
	docker tag $(TAG) registry.diginfra.net/tt/$(TAG)
	docker push registry.diginfra.net/tt/$(TAG)
	@touch $@

.PHONY: push
push: .make/.push

.PHONY: help
help:
	@echo "make-tools for $(TAG)"
	@echo "Please use \`make <target>' where <target> is one of:"
	@echo "  build           to test and build the app"
	@echo "  run-server      to start the server app"
	@echo "  push            to to push the linux/amd64 docker image to registry.diginfra.net"
	@echo "  clean           to remove generated files"
	@echo "  version-update  to update the project version"