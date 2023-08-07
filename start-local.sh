#!/usr/bin/env bash

# Execute the environment setup script
source "keys-setup.sh"

docker-compose \
  -f local-docker-compose.yml \
  --env-file service/environments/dev.env \
  up -d 2>&1 | tee tools/scripts/logs/docker-compose.log

sleep 2

docker-compose -f local-docker-compose.yml logs sketch > tools/scripts/logs/sketch.log
docker-compose -f local-docker-compose.yml logs db > tools/scripts/logs/database.log
