#!/usr/bin/env bash

set -Eeuo pipefail

SCRIPT_DIR=$(cd "$(dirname "${BASH_SOURCE[0]}")" &>/dev/null && pwd -P)

docker-compose \
  -f $SCRIPT_DIR/local-docker-compose.yml \
  stop