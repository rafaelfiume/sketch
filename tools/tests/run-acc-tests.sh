set -Eeuo pipefail

# TODO Extract it to sbt module
function sbt_subproject_run() {
  local module="$1"
  shift 1

  local command="sbt 'project $module' '${*}' >&2"
  trace "$ $command"
  eval $command
}

#
# Load 'local' environment and run acceptance tests.
#
function main() {
  local script_dir=$(cd "$(dirname "${BASH_SOURCE[0]}")" &>/dev/null && pwd -P)
  local tools_dir=$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd -P)
  local environments_dir="$tools_dir/environments"
  local utils_dir="$tools_dir/utilities"

  source "$utils_dir/logs.sh"
  source "$environments_dir/env-vars-loader.sh"

  load_env_vars "local"

  info "Running sketch acceptance tests..."

  sbt_subproject_run "testAcceptance" "test"

  info "Acceptance tests completed!"
}

main
