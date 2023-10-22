#!/usr/bin/env bash

set -Eeuo pipefail

#
# Requires: `source ${PROJECT_DIR}/tools/utilities/logs.sh`
#

#
# Load all environment variables necessary to run Sketch stack in a given environment.
#
# Only 'dev' is supported. See `${PROJECT_DIR/tools/stack/environment/dev}`.
#
function load_env_vars() {
  local env_name="$1"
  local script_dir=$(cd "$(dirname "${BASH_SOURCE[0]}")" &>/dev/null && pwd -P)
  local stack_dir="$(dirname "$script_dir")"
  local env_dir="$stack_dir/environment"

  source_files "$env_dir/$env_name"/*.sh
  source_files "$env_dir/$env_name/secrets"/*.sh
  load_keys_pair_from_pem_files_if_not_set "$env_dir" "$env_name"
}

#
# Private function: load PRIVATE_KEY and PUBLIC_KEY environment variables from pem files if they are not already set,
# which is convenient when running the stack in a developer workstation.
#
function load_keys_pair_from_pem_files_if_not_set() {
  local env_dir="$1"
  local env_name="$2"

  local private_key_file="$env_dir/$env_name/secrets/private_key.pem"
  local public_key_file="$env_dir/$env_name/secrets/public_key.pem"

  if [ -z "${PRIVATE_KEY:-}" ]; then
    PRIVATE_KEY=$(cat "$private_key_file")
    # base64 wrapping new lines is required to pass multi-line environment variables to circleci
    # See https://circleci.com/docs/set-environment-variable/#encoding-multi-line-environment-variables
    # --wrap=0 requires brew install coreutils
    trace "base64 version of PRIVATE_KEY is:\n$(echo "$PRIVATE_KEY" | base64 --wrap=0)"
    export PRIVATE_KEY
  fi

  if [ -z "${PUBLIC_KEY:-}" ]; then
    PUBLIC_KEY=$(cat "$public_key_file")
    trace "base64 version of PUBLIC_KEY:\n$(echo "$PUBLIC_KEY" | base64 --wrap=0)"
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
      trace "source $file"
      source "$file"
    fi
  done
}