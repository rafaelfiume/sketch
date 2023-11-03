#!/usr/bin/env bash

set -Eeuo pipefail

function main() {
  local script_dir=$(cd "$(dirname "${BASH_SOURCE[0]}")" &>/dev/null && pwd -P)
  local tools_dir=$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd -P)
  local environments_dir="$tools_dir/environments"
  local utils_dir="$tools_dir/utilities"
  local output_file_dir="$script_dir/data"

  source "$utils_dir/logs.sh"
  source "$environments_dir/env-vars-loader.sh"

  local env_name="local"
  local table="auth.users"
  local output_file="$output_file_dir/users_data.sql"

  load_env_vars "$env_name"

  mkdir -p "$output_file_dir"

  local command="PGPASSWORD=$DB_PASS pg_dump -h $DB_HOST -p $DB_PORT -d $DB_NAME -U $DB_USER --table=$table --data-only --file=$output_file >&2"
  debug "$command"
  eval "$command"
}

main
