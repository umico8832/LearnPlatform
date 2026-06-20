#!/usr/bin/env bash
# Load a Docker-style .env file into the current shell without evaluating its
# contents. Usage: source scripts/load-env.sh .env

if [ "$#" -ne 1 ] || [ ! -f "$1" ]; then
  echo "Usage: source scripts/load-env.sh <path-to-.env>" >&2
  return 1 2>/dev/null || exit 1
fi

while IFS= read -r line || [ -n "$line" ]; do
  case "$line" in
    ''|'#'*) continue ;;
  esac

  key=${line%%=*}
  value=${line#*=}
  [ "$key" = "$line" ] && continue

  # Docker Compose accepts quoted values; remove one matching quote pair so
  # both quoted and unquoted .env values work when exported to Maven.
  case "$value" in
    \"*\") value=${value#\"}; value=${value%\"} ;;
    \'*\') value=${value#\'}; value=${value%\'} ;;
  esac
  export "$key=$value"
done < "$1"
