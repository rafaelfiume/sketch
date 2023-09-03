#!/usr/bin/env bash

set -Eeuo pipefail

if [ -z "${CIRCLE_BRANCH:-}" ]; then
  # Running locally
  VERSION="snapshot"
else
  if [ "$CIRCLE_BRANCH" = "main" ]; then
    VERSION="${CIRCLE_BUILD_NUM}"
  else
    VERSION="${CIRCLE_BRANCH}.${CIRCLE_BUILD_NUM}"
  fi
fi

echo $VERSION
