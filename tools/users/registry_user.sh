set -Eeuo pipefail

usage() {
  cat <<EOF
Usage: $(basename "${BASH_SOURCE[0]}") [-h] [-d] [-t] --local -u username -p password

Give user access to Sketch services.

Available options:
-h, --help           Print this help and exit
  , --local          Regisre user in local environment
-u, --username       The unique identifier for the user
-p, --password       The password for the user account
-t, --trace          Enable trace level logs
EOF
  exit
}

parse_params() {
  env_name=''
  username=''
  password=''

  while :; do
    case "${1-}" in
    -h | --help) usage ;;
    --local) env_name='local' ;;
    -u | --username)
      username="${2-}"
      shift
      ;;
    -p | --password)
      password="${2-}"
      shift
      ;;
    -d | --debug) enable_debug_level ;; # see logs.sh
    -t | --trace) enable_trace_level ;; # see logs.sh
    -?*) exit_with_error "Unknown option: $1" ;;
    *) break ;;
    esac
    shift
  done

  # check required params and arguments
  # TODO Improve validation
  [[ -z "${env_name-}" ]] && exit_with_error "Missing required parameter: env_name"
  [[ -z "${username-}" ]] && exit_with_error "Missing required parameter: username"
  [[ -z "${password-}" ]] && exit_with_error "Missing required parameter: password"

  return 0
}

function main() {
  local script_dir=$(cd "$(dirname "${BASH_SOURCE[0]}")" &>/dev/null && pwd -P)
  local tools_dir=$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd -P)
  local environments_dir="$tools_dir/environments"
  local utils_dir="$tools_dir/utilities"

  source "$utils_dir/logs.sh"
  source "$utils_dir/std_sketch.sh"
  source "$utils_dir/sbt.sh"
  source "$environments_dir/env-vars-loader.sh"

  exit_if_sbt_is_not_installed

  parse_params "$@"

  info "Setting up user '$username' against '$env_name' environment..."

  # TODO Validate environment
  load_env_vars "$env_name"

  local app_name="org.fiume.sketch.auth0.scripts.UsersScript"
  sbt_run_main "auth0Scripts" "$app_name" "$username" "$password"

  info "Tell '$username' he/she is ready to go in '$env_name' environment!"
}

main "$@"
