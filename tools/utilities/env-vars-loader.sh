#!/usr/bin/env bash

set -Eeuo pipefail

#
# Requires: `source ${PROJECT_DIR}/tools/utilities/logs.sh`
# Requires: `source ${PROJECT_DIR}/tools/utilities/std.sh`
#

#
# Load all environment variables necessary to run Sketch stack in a given environment.
#
# See for instance `${PROJECT_DIR/tools/environments/dev}`.
#
load_env_vars() {
  local env_dir_param="$1"
  local env_name_param="$2"

  is_environment_supported "$env_dir_param" "$env_name_param"

  trace "Loading env vars..."

  source_files "$env_dir_param/$env_name_param"/*.sh
  source_files "$env_dir_param/$env_name_param/secrets"/*.sh
}

#
# Private function
#
is_environment_supported() {
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

  local supported_env=''
  for supported_env in ${supported_envs[@]}; do
    trace "Checking target environment '$target_environment' against supported environment '$supported_env' ..."
    if [ "$supported_env" == "$target_environment" ]; then
      debug "Found '$target_environment' environment"
      return 0
    fi
  done

  exit_with_error "Environment '$target_environment' is not supported"
}

#
# Private function: source files that matches a certain pattern.
#
source_files() {
  local path_to_files="$1"
  for file in "$path_to_files"; do
    if [ -f "$file" ]; then
      trace "Sourcing env vars from $file"
      source "$file"
    fi
  done
}
