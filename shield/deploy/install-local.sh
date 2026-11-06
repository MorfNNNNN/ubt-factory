#!/usr/bin/env bash
# Offline install from the unpacked source archive (no GitHub needed). Run as root from anywhere:
#   bash /root/semeyny-shchit-src/shield/deploy/install-local.sh familyapp1.ru
set -euo pipefail
root=$(cd "$(dirname "$0")/../.." && pwd)
[ -s "$root/shield/deploy/prebuilt/family-shield-server.jar" ] || echo "Готовой сборки сервера в архиве нет — соберу из исходников (10–20 минут)" >&2
SHIELD_LOCAL_SRC="$root" exec bash "$root/shield/deploy/install.sh" "${1:-familyapp1.ru}"
