#!/usr/bin/env bash

# Note: allows unbound variables (no 'u' option) in this script
set -Eeo pipefail

# Load PRIVATE_KEY and PUBLIC_KEY environment variables from pem files if they are not already set,
# which is convenient when running the stack in a developer workstation.

function load_keys_from_pem_files_if_not_set() {
  local script_dir=$(cd "$(dirname "${BASH_SOURCE[0]}")" &>/dev/null && pwd -P)
  local scripts_dir="$(dirname "$script_dir")"
  local envs_dir="$scripts_dir/environment"

  local private_key_file="$envs_dir/dev/secrets/private_key.pem"
  local public_key_file="$envs_dir/dev/secrets/public_key.pem"

  if [ -z "$PRIVATE_KEY" ]; then
    PRIVATE_KEY=$(cat "$private_key_file")
    # base64 wrapping new lines is required to pass multi-line environment variables to circleci
    # See https://circleci.com/docs/set-environment-variable/#encoding-multi-line-environment-variables
    # --wrap=0 requires brew install coreutils
    #echo "base64 version of PRIVATE_KEY environment variable is:<$(echo "$PRIVATE_KEY" | base64 --wrap=0)>"
    export PRIVATE_KEY
  fi

  if [ -z "$PUBLIC_KEY" ]; then
    PUBLIC_KEY=$(cat "$public_key_file")
    #echo "base64 version of PUBLIC_KEY environment variable is:<$(echo "$PUBLIC_KEY" | base64 --wrap=0)>"
    export PUBLIC_KEY
  fi
}

load_keys_from_pem_files_if_not_set
