#!/bin/sh
exec java -jar broccoli.jar server ${BR_CONFIG_FILE:-config.yml}
