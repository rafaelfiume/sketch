#!/bin/bash

PRIVATE_KEY_FILE="auth0/src/main/resources/private_key.pem"
PUBLIC_KEY_FILE="auth0/src/main/resources/public_key.pem"

if [ -z "$PRIVATE_KEY" ]; then
  PRIVATE_KEY=$(cat "$PRIVATE_KEY_FILE")
  export PRIVATE_KEY
fi

if [ -z "$PUBLIC_KEY" ]; then
  PUBLIC_KEY=$(cat "$PUBLIC_KEY_FILE")
  echo "Setting PUBLIC_KEY environment variable to: $PUBLIC_KEY"
  export PUBLIC_KEY
fi