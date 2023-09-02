#!/usr/bin/env bash

# Note: allows unbound variables (no 'u' option) in this script
# see https://betterdev.blog/minimal-safe-bash-script-template/
set -Eeo pipefail

usage() {
  cat <<EOF
Usage: $(basename "${BASH_SOURCE[0]}") [-h] [--sketch-tag tag]

Start sketch stack containers.

Available options:
-h, --help           Print this help and exit
-s, --sketch-tag     \`sketch\` image tag (default: \`latest\`)
EOF
  exit
}

parse_params() {
  sketch_image_tag='latest'

  while :; do
    case "${1-}" in
    -h | --help) usage ;;
    -s | --sketch-tag)
      sketch_image_tag="${2-}"
      shift
      ;;
    -?*) die "Unknown option: $1" ;;
    *) break ;;
    esac
    shift
  done

  [[ -z "${sketch_image_tag-}" ]] && die "Missing required parameter: sketch_image_tag"
  return 0
}

die() {
  local msg=$1
  local code=${2-1} # default exit status 1
  msg "$msg"
  exit "$code"
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

function main() {
  parse_params "$@"
  export SKETCH_IMAGE_TAG=$sketch_image_tag

  local script_dir=$(cd "$(dirname "${BASH_SOURCE[0]}")" &>/dev/null && pwd -P)
  local envs_dir="$script_dir/environment"
  local logs_dir="$script_dir/logs"

  local docker_compose_yml="$script_dir/docker-compose.yml"
  local sketch_log_file="$logs_dir/sketch.log"
  local database_log_file="$logs_dir/database.log"

  load_env_vars "dev"

  msg "Starting containers with sketch tag <$SKETCH_IMAGE_TAG>..."
  docker-compose \
    -f "$docker_compose_yml" \
    up --remove-orphans -d >&2

  msg "Checking sketch:$SKETCH_IMAGE_TAG is healthy..."
  exit_with_error_if_service_fails_to_start "http://localhost:8080/status"
  msg "\nServices have started successfully"

  write_container_logs_to_file sketch "$sketch_log_file"
  write_container_logs_to_file database "$database_log_file"
}

main "$@"
