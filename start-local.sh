#!/usr/bin/env bash

# Execute the environment setup script
source "keys-setup.sh"

docker-compose \
  -f local-docker-compose.yml \
  --env-file service/environments/dev.env \
  up -d