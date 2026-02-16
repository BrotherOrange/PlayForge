#!/usr/bin/env bash
# -------------------------------------------------------
# 推送 Docker 镜像到阿里云 ACR
#
# 首次使用需先登录:
#   docker login crpi-jvxgb8y7kie8ixpk.cn-shenzhen.personal.cr.aliyuncs.com
#
# 用法: bash push.sh [tag]
# 示例: bash push.sh latest
# -------------------------------------------------------
set -euo pipefail

# ---- 配置 ----
REGISTRY="crpi-jvxgb8y7kie8ixpk.cn-shenzhen.personal.cr.aliyuncs.com"
REMOTE_IMAGE="${REGISTRY}/play-forge/play-forge"

TAG="${1:-latest}"
LOCAL_IMAGE="playforge:${TAG}"

echo "==> Tagging: ${LOCAL_IMAGE} -> ${REMOTE_IMAGE}:${TAG}"
docker tag "${LOCAL_IMAGE}" "${REMOTE_IMAGE}:${TAG}"

echo "==> Pushing: ${REMOTE_IMAGE}:${TAG}"
docker push "${REMOTE_IMAGE}:${TAG}"
echo "==> Done: ${REMOTE_IMAGE}:${TAG}"
