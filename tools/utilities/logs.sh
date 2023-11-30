set -Eeuo pipefail

DEBUG_LOG_ENABLED=false
TRACE_LOG_ENABLED=false

setup_colors() {
  if [[ -t 2 ]] && [[ -z "${NO_COLOR-}" ]] && [[ "${TERM-}" != "dumb" ]]; then
    NOFORMAT='\033[0m' RED='\033[0;31m' GREEN='\033[0;32m' ORANGE='\033[0;33m' BLUE='\033[0;34m' PURPLE='\033[0;35m' CYAN='\033[0;36m' YELLOW='\033[1;33m'
  else
    NOFORMAT='' RED='' GREEN='' ORANGE='' BLUE='' PURPLE='' CYAN='' YELLOW=''
  fi
}

enable_trace_level() {
  DEBUG_LOG_ENABLED=true
  TRACE_LOG_ENABLED=true
}

enable_debug_level() {
  DEBUG_LOG_ENABLED=true
}

debug() {
  if [ "$DEBUG_LOG_ENABLED" = true ]; then
    echo >&2 -e "${YELLOW}> ${1-}${NOFORMAT}"
  fi
}

trace() {
  if [ "$TRACE_LOG_ENABLED" = true ]; then
    echo >&2 -e "${CYAN}> ${1-}${NOFORMAT}"
  fi
}

info() {
  echo >&2 -e "${GREEN}> ${1-}${NOFORMAT}"
}

warn() {
  echo >&2 -e "${ORANGE}> ${1-}${NOFORMAT}"
}

error() {
  echo >&2 -e "${RED}> ${1-}${NOFORMAT}"
}

setup_colors
