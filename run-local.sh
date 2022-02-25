#!/usr/bin/env bash

docker-compose \
  -f local-docker-compose.yml \
  --env-file service/environments/dev.env \
  up -d