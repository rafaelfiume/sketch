#!/usr/bin/env bash

# Note: allows unbound variables (no 'u' option) in this script
set -Eeo pipefail

SCRIPT_DIR=$(cd "$(dirname "${BASH_SOURCE[0]}")" &>/dev/null && pwd -P)
PROJECT_DIR="$(dirname "$(dirname "$SCRIPT_DIR")")"
AUTH0_DIR="${PROJECT_DIR}/auth0-scripts"

PRIVATE_KEY_FILE="$AUTH0_DIR/src/main/resources/private_key.pem"
PUBLIC_KEY_FILE="$AUTH0_DIR/src/main/resources/public_key.pem"

if [ -z "$PRIVATE_KEY" ]; then
  PRIVATE_KEY=$(cat "$PRIVATE_KEY_FILE")
  # base64 wrapping new lines is required to pass multi-line environment variables to circleci
  # See https://circleci.com/docs/set-environment-variable/#encoding-multi-line-environment-variables
  # --wrap=0 requires brew install coreutils
  #echo "base64 version of PRIVATE_KEY environment variable is:<$(echo "$PRIVATE_KEY" | base64 --wrap=0)>"
  export PRIVATE_KEY
fi

if [ -z "$PUBLIC_KEY" ]; then
  PUBLIC_KEY=$(cat "$PUBLIC_KEY_FILE")
  #echo "base64 version of PUBLIC_KEY environment variable is:<$(echo "$PUBLIC_KEY" | base64 --wrap=0)>"
  export PUBLIC_KEY
fi