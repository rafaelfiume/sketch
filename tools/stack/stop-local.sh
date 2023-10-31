#!/usr/bin/env bash

set -Eeuo pipefail

usage() {
  cat <<EOF
Usage: $(basename "${BASH_SOURCE[0]}") [-h] [-d]

Stop sketch stack containers.

Available options:
-h, --help           Print this help and exit
-d, --trace          Enable trace level logs
EOF
  exit
}

parse_params() {
  while :; do
    case "${1-}" in
    -h | --help) usage ;;
    -d | --trace) enable_trace_level ;; # see logs.sh
    -?*) exit_with_error "Unknown option: $1" ;;
    *) break ;;
    esac
    shift
  done

  return 0
}

function main() {
  local script_dir=$(cd "$(dirname "${BASH_SOURCE[0]}")" &>/dev/null && pwd -P)
  local tools_dir=$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd -P)
  local utils_dir="$tools_dir/utilities"
  local environments_dir="$tools_dir/environments"

  local docker_compose_yml="$script_dir/docker-compose.yml"

  source "$environments_dir/env-vars-loader.sh"
  source "$utils_dir/logs.sh"
  source "$utils_dir/std_sketch.sh"

  parse_params "$@"

  # Loading env vars silences docker-compose warns, eg:
  # 'WARN[0000] The "SKETCH_IMAGE_TAG" variable is not set. Defaulting to a blank string.'
  export SKETCH_IMAGE_TAG=""
  load_env_vars dev

  info "Stopping sketch stack containers..."
  local command="docker-compose -f "$docker_compose_yml" stop >&2"
  trace "$ $command"
  eval "$command"

  info "Services have stopped successfully. Have a good day!"
}

main "$@"
