all: help

TAG     := broccoli
SOURCES := $(shell find src -type f)
VERSION := $(shell cat .make/.version)
TARGET  := target/broccoli-$(VERSION).jar

.make:
	mkdir -p .make

.make/.version: .make pom.xml
	mvn help:evaluate -Dexpression=project.version -q -DforceStdout > $@

$(TARGET): .make/.version $(SOURCES) pom.xml
	mvn package

.PHONY: build
build: $(TARGET)

.PHONY: run-server
run-server: build
	java -jar $(TARGET) server config.yml

.make/.push: $(TARGET) k8s/broccoli-server/Dockerfile
	docker build -t $(TAG):$(VERSION) --platform=linux/amd64 -f k8s/broccoli-server/Dockerfile .
	docker tag $(TAG):$(VERSION) registry.diginfra.net/tt/$(TAG):$(VERSION)
	docker push registry.diginfra.net/tt/$(TAG):$(VERSION)
	@touch $@

.PHONY: push
push: .make/.push

.PHONY: clean
clean:
	rm -rf .make
	mvn clean

.PHONY: version-update
version-update:
	mvn versions:set && mvn versions:commit

.PHONY: help
help:
	@echo "make-tools for $(TAG)"
	@echo "Please use \`make <target>' where <target> is one of:"
	@echo "  build           to test and build the app"
	@echo "  run-server      to start the server app"
	@echo "  push            to to push the linux/amd64 docker image to registry.diginfra.net"
	@echo "  clean           to remove generated files"
	@echo "  version-update  to update the project version"