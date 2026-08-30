#!/usr/bin/env bash
#MISE description="Remove build output"
#MISE dir="{{config_root}}"
set -euo pipefail

exec atlas-clean
