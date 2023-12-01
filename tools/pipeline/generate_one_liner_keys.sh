#!/usr/bin/env bash

set -Eeuo pipefail

# base64 wrapping new lines is required to pass multi-line environment variables to circleci
# See https://circleci.com/docs/set-environment-variable/#encoding-multi-line-environment-variables
# --wrap=0 requires brew install coreutils

generate_one_liner_private_and_public_keys() {
  local env_name="$1"
  local script_dir=$(cd "$(dirname "${BASH_SOURCE[0]}")" &>/dev/null && pwd -P)
  local tools_dir=$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd -P)
  local environments_dir="$tools_dir/environments"
  local utils_dir="$tools_dir/utilities"
  source "$utils_dir/std.sh"
  source "$utils_dir/logs.sh"
  source "$environments_dir/load_key_pair.sh" # requires logs.sh

  enable_trace_level
  load_key_pair_from_pem_files_if_not_set "$environments_dir" "$env_name"

  if [ -n "${PRIVATE_KEY:-}" ]; then
    info "base64 PRIVATE_KEY:\n$(echo "$PRIVATE_KEY" | base64 --wrap=0)"
  else
    error "PRIVATE_KEY is not set or is empty"
  fi

  if [ -n "${PUBLIC_KEY:-}" ]; then
    info "base64 PUBLIC_KEY:\n$(echo "$PUBLIC_KEY" | base64 --wrap=0)"
  else
    error "PUBLIC_KEY is not set or is empty"
  fi
}

main() {
  generate_one_liner_private_and_public_keys dev
}

main
