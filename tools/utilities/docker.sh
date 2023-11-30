#!/usr/bin/env bash

set -Eeuo pipefail

#
# Requires: `source ${PROJECT_DIR}/tools/utilities/logs.sh`
# Requires: `source ${PROJECT_DIR}/tools/utilities/std_sketch.sh`
# Requires: `source ${PROJECT_DIR}/tools/environments/env-vars-loader.sh`
#

function login_to_docker_hub() {
  if [ -z "${DOCKER_LOGIN:-}" ] || [ -z "${DOCKER_PWD:-}" ]; then
    info "\$DOCKER_LOGIN or \$DOCKER_PWD are undefined or empty, so script must be running locally."
    load_env_vars "local"
  fi

  echo $DOCKER_PWD | docker login -u $DOCKER_LOGIN --password-stdin
}
