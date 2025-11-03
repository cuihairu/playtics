#!/usr/bin/env bash
set -euo pipefail
cd "$(dirname "$0")/bundle"
zip -r ../pit_dashboard_bundle.zip .
echo "Created: bi/superset/pit_dashboard_bundle.zip"
