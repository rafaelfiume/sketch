#!/usr/bin/env bash

set -Eeuo pipefail

usage() {
  cat <<EOF
Usage: $(basename "${BASH_SOURCE[0]}") [-h] [-t] [-d] [-p] [--sketch-tag tag]

Start sketch stack containers.

Available options:
-h, --help           Print this help and exit
-s, --sketch-tag     \`sketch\` image tag (default: \`latest\`)
-u, --sketch-ui-tag  \`sketch-ui\` image tag (default: \`latest\`)
-p, --pull           Always pull docker image before running
-d, --debug          Enable debug level logs
-t, --trace          Enable trace level logs
EOF
  exit
}

parse_params() {
  sketch_image_tag='latest'
  sketch_ui_image_tag='latest'
  pull_latest_images=''

  while :; do
    case "${1-}" in
    -h | --help) usage ;;
    -s | --sketch-tag)
      sketch_image_tag="${2-}"
      shift
      ;;
    -u | --sketch-ui-tag)
      sketch_ui_image_tag="${2-}"
      shift
      ;;
    -p | --pull) pull_latest_images="--pull=always" ;;
    -d | --debug) enable_debug_level ;; # see logs.sh
    -t | --trace) enable_trace_level ;; # see logs.sh
    -?*) exit_with_error "Unknown option: $1" ;;
    *) break ;;
    esac
    shift
  done

  return 0
}

function exit_with_error_if_service_fails_to_start() {
  local status_endpoint=$1
  wait_till_next_try_in_sec=0.3
  max_tries=20
  attempt=0
  while ! curl_output=$(curl -sSf $status_endpoint 2>&1); do
    debug "$curl_output"
    attempt=$((attempt + 1))
    if [ $attempt -ge $max_tries ]; then
      timeout=$(echo "$wait_till_next_try_in_sec * $max_tries" | bc)
      error "Failed to start application after $timeout seconds"
      exit 55
    fi
    sleep $wait_till_next_try_in_sec
  done
  debug "$curl_output"
}

function write_container_logs_to_file() {
  local container_name=$1
  local log_file=$2
  docker-compose -f "$docker_compose_yml" logs "$container_name" > "$log_file"
}

function main() {
  local script_dir=$(cd "$(dirname "${BASH_SOURCE[0]}")" &>/dev/null && pwd -P)
  local tools_dir=$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd -P)
  local env_dir="$script_dir/environment"
  local utils_dir="$tools_dir/utilities"
  local logs_dir="$script_dir/logs"

  local docker_compose_yml="$script_dir/docker-compose.yml"
  local sketch_log_file="$logs_dir/sketch.log"
  local database_log_file="$logs_dir/database.log"

  source "$env_dir/env-vars-loader.sh"
  source "$utils_dir/logs.sh"
  source "$utils_dir/std_sketch.sh"

  parse_params "$@"
  export SKETCH_IMAGE_TAG=$sketch_image_tag
  export SKETCH_UI_IMAGE_TAG=$sketch_ui_image_tag

  load_env_vars "dev"

  info "Starting containers with sketch tag '$SKETCH_IMAGE_TAG'..."
  local command="docker-compose \
    -f "$docker_compose_yml" \
    up --remove-orphans "$pull_latest_images" --detach >&2"
  trace "$ $command"
  eval "$command"

  info "Checking 'sketch:$SKETCH_IMAGE_TAG' is healthy..."
  exit_with_error_if_service_fails_to_start "http://localhost:8080/status"

  info "All services have started up like a charm!"

  write_container_logs_to_file sketch "$sketch_log_file"
  write_container_logs_to_file database "$database_log_file"
}

main "$@"
