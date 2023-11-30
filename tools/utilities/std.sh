set -Eeuo pipefail

#
# Requires: `source ${PROJECT_DIR}/tools/utilities/logs.sh`
#

exit_with_error() {
  local msg=$1
  local code=${2-1} # default exit status 1
  error "$msg"
  exit "$code"
}

run_command() {
  local command=$1

  debug "$ $command"
  eval "$command >&2"
}