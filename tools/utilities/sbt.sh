
set -Eeuo pipefail

#
# Requires: `source ${PROJECT_DIR}/tools/utilities/logs.sh`
# Requires: `source ${PROJECT_DIR}/tools/utilities/std.sh`
#

function exit_if_sbt_is_not_installed() {
  if ! command -v sbt &> /dev/null; then
    exit_with_error "Please, install sbt to run this script."
  fi
}

function sbt_subproject_run_main() {
  local module="$1"
  local app_name="$2"
  shift 2

  sbt_subproject_run "$module" "runMain $app_name ${*:-}"
}

function sbt_subproject_run() {
  local module="$1"
  local task="$2"

  local command="sbt 'project $module' '$task'"
  debug "$ $command"
  eval $command
}
