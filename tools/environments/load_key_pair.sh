#!/usr/bin/env bash

set -Eeuo pipefail

#
# Load PRIVATE_KEY and PUBLIC_KEY environment variables from pem files if they are not already set,
# which is convenient when running the stack in a developer workstation.
#
load_key_pair_from_pem_files_if_not_set() {
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
