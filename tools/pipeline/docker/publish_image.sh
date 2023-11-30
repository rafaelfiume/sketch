#!/usr/bin/env bash

set -Eeuo pipefail

function main() {
  local script_dir=$(cd "$(dirname "${BASH_SOURCE[0]}")" &>/dev/null && pwd -P)
  local tools_dir=$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd -P)
  local environments_dir="$tools_dir/environments"
  local utils_dir="$tools_dir/utilities"

  source "$utils_dir/std_sketch.sh"
  source "$utils_dir/logs.sh"
  source "$utils_dir/sbt.sh"
  source "$environments_dir/env-vars-loader.sh"
  source "$utils_dir/docker.sh"

  exit_if_sbt_is_not_installed

  login_to_docker_hub

  docker push rafaelfiume/sketch:$VERSION
  docker push rafaelfiume/sketch:latest
  if [ "$CIRCLE_BRANCH" = "main" ]; then
    docker push rafaelfiume/sketch:stable
  fi
}

main
