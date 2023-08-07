#!/usr/bin/env bash

# Note: allows unbound variables (no 'u' option) in this script
# see https://betterdev.blog/minimal-safe-bash-script-template/
set -Eeo pipefail

SCRIPT_DIR=$(cd "$(dirname "${BASH_SOURCE[0]}")" &>/dev/null && pwd -P)
PROJECT_DIR="$(dirname "$(dirname "$SCRIPT_DIR")")"
ENV_DIR="${PROJECT_DIR}/service/environments"
LOG_DIR="$SCRIPT_DIR/logs"

# Execute the environment setup script
source "$SCRIPT_DIR/keys-setup.sh"

docker-compose \
  -f $SCRIPT_DIR/local-docker-compose.yml \
  --env-file $ENV_DIR/dev.env \
  up -d 2>&1 | tee $LOG_DIR/docker-compose.log

sleep 1

docker-compose -f $SCRIPT_DIR/local-docker-compose.yml logs sketch > $LOG_DIR/sketch.log
docker-compose -f $SCRIPT_DIR/local-docker-compose.yml logs database > $LOG_DIR/database.log
