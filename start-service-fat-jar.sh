#!/usr/bin/env bash

set -o allexport
source service/environments/dev.env

exec java -jar service/target/scala-3.1.1/sketch-assembly-dev.jar
