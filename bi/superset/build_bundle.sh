#!/usr/bin/env bash
set -euo pipefail
cd "$(dirname "$0")/bundle"
zip -r ../playtics_dashboard_bundle.zip .
echo "Created: bi/superset/playtics_dashboard_bundle.zip"
