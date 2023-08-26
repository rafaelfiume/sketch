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

# Set the tag env var to the first argument passed to the script
SKETCH_IMAGE_TAG_ARG=${1:-latest}
export SKETCH_IMAGE_TAG=$SKETCH_IMAGE_TAG_ARG

echo "Starting local environment with sketch tag: $SKETCH_IMAGE_TAG"

docker-compose \
  -f $SCRIPT_DIR/local-docker-compose.yml \
  --env-file $ENV_DIR/dev.env \
  up -d 2>&1 | tee $LOG_DIR/docker-compose.log

sleep 2

docker-compose -f $SCRIPT_DIR/local-docker-compose.yml logs sketch > $LOG_DIR/sketch.log
docker-compose -f $SCRIPT_DIR/local-docker-compose.yml logs database > $LOG_DIR/database.log
