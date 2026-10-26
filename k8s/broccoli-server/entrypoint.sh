#!/bin/sh
exec java ${BR_JAVA_OPTS:--XX:MaxRAMPercentage=80} -jar broccoli.jar server ${BR_CONFIG_FILE:-config.yml}
