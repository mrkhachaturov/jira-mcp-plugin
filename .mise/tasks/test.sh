#!/usr/bin/env bash
#MISE description="Unit tests (everything except the live e2e suite)"
#MISE dir="{{config_root}}"
set -euo pipefail

exec atlas-mvn test -Dtest='!*E2E*'
