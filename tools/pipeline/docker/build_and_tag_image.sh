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
  source "$script_dir/std.sh"

  exit_if_sbt_is_not_installed

  if [ -z "${DOCKER_LOGIN:-}" ] || [ -z "${DOCKER_PWD:-}"]; then
    load_env_vars "local"
  fi

  sbt "service/docker:publishLocal"
  if [ "${CIRCLE_BRANCH:-}" = "main" ]; then
    login_to_docker_hub
    docker tag rafaelfiume/sketch:$VERSION rafaelfiume/sketch:stable
  fi
}

main
