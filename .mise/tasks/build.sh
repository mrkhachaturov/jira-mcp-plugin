#!/usr/bin/env bash
#MISE description="Build the plugin JAR/OBR"
#MISE depends=["build:app"]
#MISE wait_for=["clean"]
#MISE dir="{{config_root}}"
set -euo pipefail

# atlas-package wraps Maven with the Atlassian repositories; plain mvn resolves nothing locally.
exec atlas-package -DskipTests
