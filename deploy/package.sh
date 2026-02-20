#!/usr/bin/env bash
# -------------------------------------------------------
# 构建 PlayForge 前后端合一 fat JAR
#
# 用法: bash package.sh
# 产物: playforge-start/target/playforge-start-0.0.1-SNAPSHOT.jar
# -------------------------------------------------------
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"
PROJECT_ROOT="$(cd "${SCRIPT_DIR}/.." && pwd)"

echo "==> [1/3] Building frontend"
cd "${PROJECT_ROOT}/frontend"
npm ci --no-audit --no-fund --legacy-peer-deps
npm run build

echo "==> [2/3] Copying frontend assets to Spring Boot static resources"
STATIC_DIR="${PROJECT_ROOT}/playforge-start/src/main/resources/static"
rm -rf "${STATIC_DIR}"
cp -r build/ "${STATIC_DIR}"

echo "==> [3/3] Building fat JAR"
cd "${PROJECT_ROOT}"
./mvnw clean package -DskipTests -B

JAR_PATH=$(ls "${PROJECT_ROOT}/playforge-start/target"/playforge-start-*.jar 2>/dev/null | head -1)
echo "==> Done: ${JAR_PATH}"
