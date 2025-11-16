#!/bin/bash

set -e

ZK_PATH="/vectordb/cluster/config"
MAX_RETRIES=30
RETRY_INTERVAL=2

echo "Waiting for ZooKeeper container to be ready..."
RETRIES=0
while [ $RETRIES -lt $MAX_RETRIES ]; do
  if docker exec vector-db-zookeeper zkCli.sh ls / >/dev/null 2>&1; then
    echo "ZooKeeper is ready!"
    break
  fi
  RETRIES=$((RETRIES + 1))
  if [ $RETRIES -eq $MAX_RETRIES ]; then
    echo "ERROR: ZooKeeper failed to start after ${MAX_RETRIES} attempts"
    exit 1
  fi
  echo "ZooKeeper is not ready yet, attempt $RETRIES/$MAX_RETRIES, sleeping ${RETRY_INTERVAL}s..."
  sleep $RETRY_INTERVAL
done

STORAGE1_URL="${STORAGE1_URL:-http://vector-db-storage-1:8081}"
STORAGE2_URL="${STORAGE2_URL:-http://vector-db-storage-2:8081}"

# Для 2 шардов равномерно делим пространство [0, Long.MAX_VALUE]:
HALF_HASH=4611686018427387903  # Long.MAX_VALUE / 2

CONFIG_JSON='{"shards":['\
'{"shardId":"shard1","baseUrl":"'${STORAGE1_URL}'","hashKey":0,"status":"ACTIVE"},'\
'{"shardId":"shard2","baseUrl":"'${STORAGE2_URL}'","hashKey":'${HALF_HASH}',"status":"ACTIVE"}'\
'],"metadata":{}}'

echo "Ensuring parent znodes exist..."
docker exec vector-db-zookeeper zkCli.sh create /vectordb "" >/dev/null 2>&1 || true
docker exec vector-db-zookeeper zkCli.sh create /vectordb/cluster "" >/dev/null 2>&1 || true

echo "Checking if cluster configuration already exists..."
if docker exec vector-db-zookeeper zkCli.sh get ${ZK_PATH} >/dev/null 2>&1; then
  echo "Configuration already exists, checking if it needs update..."
  EXISTING_CONFIG=$(docker exec vector-db-zookeeper zkCli.sh get ${ZK_PATH} 2>/dev/null | grep -A 100 "^{" || true)
  
  if [ -n "$EXISTING_CONFIG" ]; then
    echo "Current configuration found:"
    echo "$EXISTING_CONFIG" | python3 -m json.tool 2>/dev/null || echo "$EXISTING_CONFIG"
    echo ""
    echo "Updating to new configuration..."
    docker exec vector-db-zookeeper zkCli.sh set ${ZK_PATH} "${CONFIG_JSON}" >/dev/null 2>&1
    echo "Configuration updated successfully!"
  else
    echo "WARNING: Configuration node exists but is empty, recreating..."
    docker exec vector-db-zookeeper zkCli.sh delete ${ZK_PATH} >/dev/null 2>&1 || true
    docker exec vector-db-zookeeper zkCli.sh create ${ZK_PATH} "${CONFIG_JSON}" >/dev/null 2>&1
    echo "Configuration created successfully!"
  fi
else
  echo "Configuration does not exist, creating new..."
  docker exec vector-db-zookeeper zkCli.sh create ${ZK_PATH} "${CONFIG_JSON}" >/dev/null 2>&1
  echo "Configuration created successfully!"
fi

echo ""
echo "Current cluster configuration:"
echo "${CONFIG_JSON}" | python3 -m json.tool 2>/dev/null || echo "${CONFIG_JSON}"
echo ""
echo "Cluster initialization complete!"

