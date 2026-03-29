#!/bin/bash

PROJECT_DIR="/root/Public/flux-drive"
cd "$PROJECT_DIR" || exit 1

# 定义服务与目录的映射
declare -A SERVICE_MAP=(
    ["gateway-service"]="gateway"
    ["storage-service"]="storage-service"
    ["user-service"]="user-service"
    ["file-system"]="file-system"
)

# 定义共享模块
SHARED_MODULES=("dubbo-api" "amqp-api" "pom.xml" "docker-compose.yml")

# Git pull 并获取变更文件
OUTPUT=$(git pull 2>&1)
echo "[$(date)] Git output:"
echo "$OUTPUT"

# 检查是否有更新
if echo "$OUTPUT" | grep -q "Already up to date"; then
    echo "No changes, exit"
    exit 0
fi

# 提取变更的文件列表
CHANGED_FILES=$(git diff --name-only HEAD@{1} HEAD 2>/dev/null || git diff --name-only HEAD~1 HEAD)

echo "[$(date)] Changed files:"
echo "$CHANGED_FILES"

# 检查共享模块（影响所有服务）
if echo "$CHANGED_FILES" | grep -qE "^(pom\.xml|dubbo-api/|amqp-api/|docker-compose\.yml)"; then
    echo "Shared module changed, rebuilding all services"
    SERVICES_TO_BUILD="gateway storage-service user-service file-system"
else
    # 检测哪些服务需要构建
    SERVICES_TO_BUILD=""
    for dir in "${!SERVICE_MAP[@]}"; do
        if echo "$CHANGED_FILES" | grep -q "^$dir/"; then
            service_name="${SERVICE_MAP[$dir]}"
            SERVICES_TO_BUILD="$SERVICES_TO_BUILD $service_name"
            echo "Detected change in $dir -> will build $service_name"
        fi
    done
fi

# 执行构建
if [ -n "$SERVICES_TO_BUILD" ]; then
    echo "[$(date)] Building:$SERVICES_TO_BUILD"
    docker compose up -d --build $SERVICES_TO_BUILD
    echo "[$(date)] Build completed"
else
    echo "[$(date)] No service changes detected"
fi