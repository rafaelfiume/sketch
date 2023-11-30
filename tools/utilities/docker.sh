#!/usr/bin/env bash

set -Eeuo pipefail

function login_to_docker_hub() {
  echo $DOCKER_PWD | docker login -u $DOCKER_LOGIN --password-stdin
}
