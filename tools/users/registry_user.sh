set -Eeuo pipefail

usage() {
  cat <<EOF
Usage: $(basename "${BASH_SOURCE[0]}") [-h] --dev -u username -p password

Give user access to Sketch services.

Available options:
-h, --help           Print this help and exit
-u, --username       The unique identifier for the user
-p, --password       The password for the user account
-d, --debug          Enable debug level logs
-t, --trace          Enable trace level logs
EOF
  exit
}

parse_params() {
  env=''
  username=''
  password=''

  while :; do
    case "${1-}" in
    -h | --help) usage ;;
    -f | --dev) env='dev' ;;
    -u | --username)
      username="${2-}"
      shift
      ;;
    -p | --password)
      password="${2-}"
      shift
      ;;
    -?*) exit_with_error "Unknown option: $1" ;;
    *) break ;;
    esac
    shift
  done

  args=("$@")

  echo "Username: $username"
  echo "Password: $password"

  # check required params and arguments
  # Test with "abacaxinaofazxixi" "@"
  # TODO Improve validation
  [[ -z "${env-}" ]] && exit_with_error "Missing required parameter: env"
  [[ -z "${username-}" ]] && exit_with_error "Missing required parameter: username"
  [[ -z "${password-}" ]] && exit_with_error "Missing required parameter: password"

  return 0
}

#
# Requires: `source "$utils_dir/std_sketch.sh"`
#
function exit_if_sbt_is_not_installed() {
  if ! command -v sbt &> /dev/null; then
    exit_with_error "Please install sbt to run this application"
  fi
}

function run_sbt() {
  local module="$1"
  local app_name="$2"
  shift 2

  sbt "project $module" "runMain $app_name ${*}"
}

function main() {
  local script_dir=$(cd "$(dirname "${BASH_SOURCE[0]}")" &>/dev/null && pwd -P)
  local tools_dir=$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd -P)
  local environments_dir="$tools_dir/environments"
  local utils_dir="$tools_dir/utilities"

  source "$utils_dir/logs.sh"
  source "$utils_dir/std_sketch.sh"
  source "$environments_dir/env-vars-loader.sh"

  exit_if_sbt_is_not_installed

  parse_params "$@"

  # TODO Validate environment
  load_env_vars "$env"

  # TODO "stdout is for output, and stderr is for messages
  # Log
  local app_name="org.fiume.sketch.auth0.scripts.UsersScript"
  run_sbt "auth0Scripts" "$app_name" "$username" "$password"

  EXIT_CODE=$?
  if [ $EXIT_CODE -eq 0 ]; then
    echo "Scala program executed successfully."
  else
    echo "Scala program exited with an error. Exit code: $EXIT_CODE"
  fi
}

main "$@"
