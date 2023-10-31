#!/usr/bin/env bash

set -Eeuo pipefail

#
# Load all environment variables necessary to run Sketch stack in a given environment.
#
# See for instance `${PROJECT_DIR/tools/environments/dev}`.
#
function load_env_vars() {
  local env_name="$1"
  local script_dir=$(cd "$(dirname "${BASH_SOURCE[0]}")" &>/dev/null && pwd -P)
  local tools_dir=$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd -P)
  local utils_dir="$tools_dir/utilities"

  source "$utils_dir/logs.sh"

  trace "Loading env vars..."

  source_files "$script_dir/$env_name"/*.sh
  source_files "$script_dir/$env_name/secrets"/*.sh
  load_key_pair_from_pem_files_if_not_set "$script_dir" "$env_name"
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
