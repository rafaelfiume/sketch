
set -Eeuo pipefail

#
# Requires: `source "$utils_dir/logs.sh"`
# Requires: `source "$utils_dir/std_sketch.sh"`
#

function exit_if_sbt_is_not_installed() {
  if ! command -v sbt &> /dev/null; then
    exit_with_error "Please install sbt to run this application"
  fi
}

function sbt_run_main() {
  local module="$1"
  local app_name="$2"
  shift 2

  sbt_subproject_run "$module" "runMain $app_name ${*}"
}

function sbt_subproject_run() {
  local module="$1"
  shift 1

  local command="sbt 'project $module' '${*}' >&2"
  debug "$ $command"
  eval $command
}
