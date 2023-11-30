#!/usr/bin/env bash

set -Eeuo pipefail

#
# Requires: `source ${PROJECT_DIR}/tools/utilities/std.sh`
#

function login_to_docker_hub() {
  echo $DOCKER_PWD | docker login -u $DOCKER_LOGIN --password-stdin
}

function exit_if_docker_is_not_installed() {
  if ! command -v docker &> /dev/null; then
    exit_with_error "Please, install docker to run this this script."
  fi
}
