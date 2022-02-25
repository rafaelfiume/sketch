#!/usr/bin/env bash

set -euo pipefail

# Stops sbt starting a daemon in this subshell
unset SBT_NATIVE_CLIENT

sbt service/run
