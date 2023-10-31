set -Eeuo pipefail

# TODO Extract it to sbt module
function sbt_subproject_run() {
  local module="$1"
  shift 1

  sbt "project $module" "${*}"
}

#
# Load 'local' environment and run acceptance tests.
#
function main() {
  local script_dir=$(cd "$(dirname "${BASH_SOURCE[0]}")" &>/dev/null && pwd -P)
  local tools_dir=$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd -P)
  local environments_dir="$tools_dir/environments"

  source "$environments_dir/env-vars-loader.sh"

  load_env_vars "local"

  sbt_subproject_run "testAcceptance" "test"
}

main
