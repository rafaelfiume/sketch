set -Eeuo pipefail

#
# Load 'local' environment and run acceptance tests.
#
function main() {
  local script_dir=$(cd "$(dirname "${BASH_SOURCE[0]}")" &>/dev/null && pwd -P)
  local tools_dir=$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd -P)
  local environments_dir="$tools_dir/environments"
  local utils_dir="$tools_dir/utilities"

  source "$utils_dir/std.sh"
  source "$utils_dir/logs.sh"
  source "$utils_dir/sbt.sh"
  source "$utils_dir/env-vars-loader.sh"

  exit_if_sbt_is_not_installed

  load_env_vars "$environments_dir" "local"

  info "Running sketch load tests..."

  sbt_subproject_run "testAcceptance" "Gatling/test"

  info "Load tests completed!"
}

main
