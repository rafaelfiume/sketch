#!/usr/bin/env bash

set -Eeuo pipefail

#
# Load all environment variables necessary to run Sketch stack in a given environment.
#
# For a list of supported environments, see `${PROJECT_DIR/tools/stack/environments}` subdirectories.
# It will likely include at least one `.sh` file for each service/resource that composes the stack.
# For example, `sketch.env.sh`.
#
function load_env_vars() {
  local environment_name=$1
  for file in "$envs_dir/$environment_name"/*.sh; do
    if [ -f "$file" ]; then
      source "$file"
    fi
  done

  load_keys_from_pem_files_if_not_set "$environment_name"
}

#
# Load PRIVATE_KEY and PUBLIC_KEY environment variables from pem files if they are not already set,
# which is convenient when running the stack in a developer workstation.
#
# Requires: `source ${PROJECT_DIR}/tools/utilities/logs.sh`
#
function load_keys_from_pem_files_if_not_set() {
  local script_dir=$(cd "$(dirname "${BASH_SOURCE[0]}")" &>/dev/null && pwd -P)
  local stack_dir="$(dirname "$script_dir")"
  local envs_dir="$stack_dir/environment"

  local private_key_file="$envs_dir/$environment_name/secrets/private_key.pem"
  local public_key_file="$envs_dir/$environment_name/secrets/public_key.pem"

  if [ -z "${PRIVATE_KEY:-}" ]; then
    PRIVATE_KEY=$(cat "$private_key_file")
    # base64 wrapping new lines is required to pass multi-line environment variables to circleci
    # See https://circleci.com/docs/set-environment-variable/#encoding-multi-line-environment-variables
    # --wrap=0 requires brew install coreutils
    trace "base64 version of PRIVATE_KEY is:\n$(echo "$PRIVATE_KEY" | base64 --wrap=0)\n"
    export PRIVATE_KEY
  fi

  if [ -z "${PUBLIC_KEY:-}" ]; then
    PUBLIC_KEY=$(cat "$public_key_file")
    trace "base64 version of PUBLIC_KEY:\n$(echo "$PUBLIC_KEY" | base64 --wrap=0)\n"
    export PUBLIC_KEY
  fi
}
