#!/bin/sh
#
# Use this script to run your program LOCALLY.
#
# Note: Changing this script WILL NOT affect how CodeCrafters runs your program.
#
# Learn more: https://codecrafters.io/program-interface

set -e # Exit early if any commands fail

project_dir=$(CDPATH= cd "$(dirname "$0")" && pwd)

(
  cd "$project_dir"
  mvn -q -B package -DskipTests
)

exec java -jar "$project_dir/target/codecrafters-shell.jar" "$@"
