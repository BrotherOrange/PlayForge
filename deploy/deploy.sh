#!/usr/bin/env bash
# -------------------------------------------------------
# 部署 PlayForge 到阿里云 SAE (Serverless 应用引擎)
#
# 前置条件:
#   1. 安装 aliyun CLI 并配置: aliyun configure
#   2. 已执行 build.sh 和 push.sh 将镜像推到 ACR
#
# 用法: bash deploy.sh [tag]
# 示例: bash deploy.sh latest
# -------------------------------------------------------
set -euo pipefail

# ---- 配置 ----
APP_ID="28f49a71-fafb-48df-b7c8-b1f2590a01b9"
REGISTRY="crpi-jvxgb8y7kie8ixpk.cn-shenzhen.personal.cr.aliyuncs.com"
IMAGE="${REGISTRY}/play-forge/play-forge"

TAG="${1:-latest}"
IMAGE_URL="${IMAGE}:${TAG}"

echo "==> Deploying to SAE (game-design-app)"
echo "    App ID : ${APP_ID}"
echo "    Image  : ${IMAGE_URL}"

aliyun sae DeployApplication \
  --AppId "${APP_ID}" \
  --ImageUrl "${IMAGE_URL}"

echo "==> Deploy request submitted. Check SAE console for status."
