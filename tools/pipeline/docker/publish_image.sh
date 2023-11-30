#!/usr/bin/env bash

set -Eeuo pipefail

exit_if_version_is_undefined() {
  if [ -z "${VERSION:-}" ]; then
    exit_with_error "Fail to publish image: \$VERSION is undefined or empty."
  fi
}

do_login_to_docker_hub() {
  if [ -z "${DOCKER_LOGIN:-}" ] || [ -z "${DOCKER_PWD:-}" ]; then
    info "\$DOCKER_LOGIN or \$DOCKER_PWD are undefined or empty, so script must be running locally."
    load_env_vars "$environments_dir" "local"
  fi
  login_to_docker_hub
}

main() {
  local script_dir=$(cd "$(dirname "${BASH_SOURCE[0]}")" &>/dev/null && pwd -P)
  local tools_dir=$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd -P)
  local environments_dir="$tools_dir/environments"
  local utils_dir="$tools_dir/utilities"
  source "$utils_dir/logs.sh"
  source "$utils_dir/std.sh"
  source "$utils_dir/sbt.sh"
  source "$utils_dir/env-vars-loader.sh"
  source "$utils_dir/docker.sh"

  info "Publishing image to Docker Hub..."

  exit_if_sbt_is_not_installed
  exit_if_docker_is_not_installed
  exit_if_version_is_undefined

  do_login_to_docker_hub

  run_command "docker push rafaelfiume/sketch:latest"
  run_command "docker push rafaelfiume/sketch:$VERSION"
  if [ "${CIRCLE_BRANCH:-}" = "main" ]; then
    run_command "docker push rafaelfiume/sketch:stable"
  else
    info "Skipping publishing 'stable' tag (branch != 'main')."
  fi

  info "Image successfully published."
}

main
