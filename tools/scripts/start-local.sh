#!/usr/bin/env bash

# Note: allows unbound variables (no 'u' option) in this script
# see https://betterdev.blog/minimal-safe-bash-script-template/
set -Eeo pipefail

setup_colors() {
  if [[ -t 2 ]] && [[ -z "${NO_COLOR-}" ]] && [[ "${TERM-}" != "dumb" ]]; then
    NOFORMAT='\033[0m' RED='\033[0;31m' GREEN='\033[0;32m' ORANGE='\033[0;33m' BLUE='\033[0;34m' PURPLE='\033[0;35m' CYAN='\033[0;36m' YELLOW='\033[1;33m'
  else
    NOFORMAT='' RED='' GREEN='' ORANGE='' BLUE='' PURPLE='' CYAN='' YELLOW=''
  fi
}

msg() {
  echo >&2 -e "${1-}"
}

function load_env_vars() {
  local environment_name=$1
  for file in "$envs_dir/$environment_name"/*.sh; do
    if [ -f "$file" ]; then
      source "$file"
    fi
  done
  source "$envs_dir/load-keys-pair-if-not-set.sh"
}

function exit_with_error_if_service_fails_to_start() {
  local status_endpoint=$1
  wait_till_next_try_in_sec=0.3
  max_tries=20
  attempt=0
  while ! curl -sSf $status_endpoint >&2; do
    attempt=$((attempt + 1))
    if [ $attempt -ge $max_tries ]; then
      timeout=$(msg "$wait_till_next_try_in_sec * $max_tries" | bc)
      msg "Failed to start application after $timeout seconds"
      exit 1
    fi
    sleep $wait_till_next_try_in_sec
  done
}

function write_container_logs_to_file() {
  local container_name=$1
  local log_file=$2
  docker-compose -f "$docker_compose_yml" logs "$container_name" > "$log_file"
}

# $1: sketch image tag (default: latest)
function start_containers() {
  local sketch_image_tag_arg=${1:-latest}
  export SKETCH_IMAGE_TAG=$sketch_image_tag_arg

  local script_dir=$(cd "$(dirname "${BASH_SOURCE[0]}")" &>/dev/null && pwd -P)
  local envs_dir="$script_dir/environment"
  local logs_dir="$script_dir/logs"

  local docker_compose_yml="$script_dir/docker-compose.yml"
  local sketch_log_file="$logs_dir/sketch.log"
  local database_log_file="$logs_dir/database.log"

  load_env_vars "dev"

  msg "Starting local environment with sketch tag <$SKETCH_IMAGE_TAG>..."
  docker-compose \
    -f "$docker_compose_yml" \
    up --remove-orphans -d >&2

  exit_with_error_if_service_fails_to_start "http://localhost:8080/status"

  write_container_logs_to_file sketch "$sketch_log_file"
  write_container_logs_to_file database "$database_log_file"
}

start_containers $1
