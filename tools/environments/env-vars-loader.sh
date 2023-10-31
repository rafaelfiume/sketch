#!/usr/bin/env bash

set -Eeuo pipefail

#
# Requires: `source ${PROJECT_DIR}/tools/utilities/logs.sh`
# Requires: `source "$utils_dir/std_sketch.sh"`
#

#
# Load all environment variables necessary to run Sketch stack in a given environment.
#
# See for instance `${PROJECT_DIR/tools/environments/dev}`.
#
function load_env_vars() {
  local env_name="$1"
  local script_dir=$(cd "$(dirname "${BASH_SOURCE[0]}")" &>/dev/null && pwd -P)

  is_environment_supported "$script_dir" "$env_name"

  trace "Loading env vars..."

  source_files "$script_dir/$env_name"/*.sh
  source_files "$script_dir/$env_name/secrets"/*.sh
  load_key_pair_from_pem_files_if_not_set "$script_dir" "$env_name"
}

#
# Private function
#
function is_environment_supported() {
  local environments_dir="$1"
  local target_environment="$2"

  local env_name=''
  local supported_envs=()
  for env_dir in "$environments_dir"/*/; do
    if [ -d $env_dir ]; then
      env_name=$(basename "$env_dir")
      supported_envs+=("$env_name")
    fi
  done
  trace "Found the following environments: '${supported_envs[*]}'"

  local env=''
  for env in ${supported_envs[@]}; do
    trace "Checking '$env' against target enviroment '$target_environment'..."
    if [ "$env" == "$target_environment" ]; then
      debug "Found '$env' environment"
      return 0
    fi
  done

  exit_with_error "Environment '$target_environment' is not supported"
}

#
# Private function: load PRIVATE_KEY and PUBLIC_KEY environment variables from pem files if they are not already set,
# which is convenient when running the stack in a developer workstation.
#
function load_key_pair_from_pem_files_if_not_set() {
  local environments_dir="$1"
  local env_name="$2"

  local private_key_file="$environments_dir/$env_name/secrets/private_key.pem"
  local public_key_file="$environments_dir/$env_name/secrets/public_key.pem"

  if [ -z "${PRIVATE_KEY:-}" ]; then
    PRIVATE_KEY=$(cat "$private_key_file")
    trace "PRIVATE_KEY was not set. Exporting PRIVATE_KEY:\n$PRIVATE_KEY"
    export PRIVATE_KEY
  fi

  if [ -z "${PUBLIC_KEY:-}" ]; then
    PUBLIC_KEY=$(cat "$public_key_file")
    trace "PUBLIC_KEY was not set. Exporting PUBLIC_KEY:\n$PUBLIC_KEY"
    export PUBLIC_KEY
  fi
}

#
# Private function: source files that matches a certain pattern.
#
function source_files() {
  local path_to_files="$1"
  for file in "$path_to_files"; do
    if [ -f "$file" ]; then
      trace "Sourcing env vars from $file"
      source "$file"
    fi
  done
}
