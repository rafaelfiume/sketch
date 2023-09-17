#!/usr/bin/env bash

set -Eeuo pipefail

usage() {
  cat <<EOF
Usage: $(basename "${BASH_SOURCE[0]}") [-h] [-d]

Stop sketch stack containers.

Available options:
-h, --help           Print this help and exit
-d, --debug          Enable debug level logs
EOF
  exit
}

parse_params() {
  while :; do
    case "${1-}" in
    -h | --help) usage ;;
    -d | --debug) enable_debug_level ;; # see logs.sh
    -?*) die "Unknown option: $1" ;;
    *) break ;;
    esac
    shift
  done

  return 0
}

function load_env_vars() {
  local environment_name=$1
  for file in "$envs_dir/$environment_name"/*.sh; do
    if [ -f "$file" ]; then
      source "$file"
    fi
  done
  source "$envs_dir/load-keys-pair-if-not-set.sh"
  load_keys_from_pem_files_if_not_set
}

function main() {
  local script_dir=$(cd "$(dirname "${BASH_SOURCE[0]}")" &>/dev/null && pwd -P)
  local utils_dir="$script_dir/utilities"
  local envs_dir="$script_dir/environment"

  local docker_compose_yml="$script_dir/docker-compose.yml"

  source "$utils_dir/logs.sh"

  parse_params "$@"

  # Loading env vars silences docker-compose warns, eg:
  # 'WARN[0000] The "SKETCH_IMAGE_TAG" variable is not set. Defaulting to a blank string.'
  export SKETCH_IMAGE_TAG=""
  load_env_vars dev

  info "Stopping sketch stack containers..."
  local command="docker-compose -f "$docker_compose_yml" stop >&2"
  debug "$ $command"
  eval "$command"

  info "Services have stopped successfully. Have a good day!"
}

main "$@"
