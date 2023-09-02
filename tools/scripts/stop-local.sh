#!/usr/bin/env bash

# Note: allows unbound variables (no 'u' option) in this script
# see https://betterdev.blog/minimal-safe-bash-script-template/
set -Eeo pipefail

usage() {
  cat <<EOF
Usage: $(basename "${BASH_SOURCE[0]}") [-h]

Stop sketch stack containers.

Available options:
-h, --help           Print this help and exit
EOF
  exit
}

parse_params() {
  while :; do
    case "${1-}" in
    -h | --help) usage ;;
    -?*) die "Unknown option: $1" ;;
    *) break ;;
    esac
    shift
  done

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

function main() {
  parse_params "$@"

  local script_dir=$(cd "$(dirname "${BASH_SOURCE[0]}")" &>/dev/null && pwd -P)
  local envs_dir="$script_dir/environment"

  local docker_compose_yml="$script_dir/docker-compose.yml"

  # Loading env vars silences docker-compose warns, eg:
  # 'WARN[0000] The "SKETCH_IMAGE_TAG" variable is not set. Defaulting to a blank string.'
  export SKETCH_IMAGE_TAG=""
  load_env_vars dev

  docker-compose \
    -f "$docker_compose_yml" \
    stop >&2
}

main "$@"
