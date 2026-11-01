#!/bin/sh
set -eu

SCRIPT_DIR=$(CDPATH= cd -- "$(dirname -- "$0")" && pwd)
VENV_DIR=${RELAYFORGE_ROBOT_VENV:-"$SCRIPT_DIR/.venv"}
ROBOT="$VENV_DIR/bin/robot"

if [ ! -x "$ROBOT" ]; then
  python3 -m venv "$VENV_DIR"
  "$VENV_DIR/bin/python" -m pip install --disable-pip-version-check -r "$SCRIPT_DIR/requirements.txt"
fi

SUITE=${1:-all}
if [ "$#" -gt 0 ]; then shift; fi
case "$SUITE" in
  all) set -- "$SCRIPT_DIR/delivery.robot" "$@" ;;
  accept) set -- --test "Accepted Delivery Reaches Receiver Once" "$SCRIPT_DIR/delivery.robot" "$@" ;;
  retry) set -- --test "HTTP 503 Is Retried Until Attempts Are Exhausted" "$SCRIPT_DIR/delivery.robot" "$@" ;;
  *)
    echo "Usage: ./rf test [all|accept|retry] [Robot Framework options]" >&2
    exit 2
    ;;
esac

exec "$ROBOT" --outputdir "$SCRIPT_DIR/results" "$@"
