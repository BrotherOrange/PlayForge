#!/usr/bin/env bash
# -------------------------------------------------------
# 构建 PlayForge Docker 镜像
# 用法: bash build.sh [tag]
# 示例: bash build.sh latest
#        bash build.sh 1.0.0
# -------------------------------------------------------
set -euo pipefail

TAG="${1:-latest}"
IMAGE_NAME="playforge:${TAG}"

SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"
PROJECT_ROOT="$(cd "${SCRIPT_DIR}/.." && pwd)"

echo "==> Building image: ${IMAGE_NAME}"
docker build --platform linux/amd64 -t "${IMAGE_NAME}" "${PROJECT_ROOT}"
echo "==> Done: ${IMAGE_NAME}"
