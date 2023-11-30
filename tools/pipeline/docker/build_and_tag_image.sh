#!/usr/bin/env bash

set -Eeuo pipefail

function main() {
  local script_dir=$(cd "$(dirname "${BASH_SOURCE[0]}")" &>/dev/null && pwd -P)
  local tools_dir=$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd -P)
  local environments_dir="$tools_dir/environments"
  local utils_dir="$tools_dir/utilities"

  source "$utils_dir/std.sh"
  source "$utils_dir/logs.sh"
  source "$utils_dir/sbt.sh"
  source "$utils_dir/env-vars-loader.sh"
  source "$utils_dir/docker.sh"

  exit_if_sbt_is_not_installed
  exit_if_docker_is_not_installed

  sbt "service/docker:publishLocal"

  if [ "${CIRCLE_BRANCH:-}" = "main" ] && [ -n "${VERSION:-}" ]; then
    docker tag rafaelfiume/sketch:$VERSION rafaelfiume/sketch:stable
  fi
}

main
