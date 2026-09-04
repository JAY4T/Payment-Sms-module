#!/bin/bash

# Exit on error
set -e

# Check if commit hash is passed as an argument
if [ -z "$1" ]; then
  echo "Usage: $0 <commit-hash>"
  exit 1
fi

COMMIT_HASH=$1
RELEASES_DIR="/home/pipeline/releases/paymentsms_dev"
DEPLOY_BIN="/home/pipeline/production/paymentsms_dev/paymentsms.jar"
SERVICE_NAME="paymentsms_dev.service"
BINARY_NAME="paymentsms-${COMMIT_HASH}.jar"
PORT="8010"

# Check if the binary exists
if [ ! -f "${RELEASES_DIR}/${BINARY_NAME}" ]; then
  echo "Binary ${BINARY_NAME} not found in ${RELEASES_DIR}"
  exit 1
fi

# Keep a reference to the previous binary from the symlink
if [ -L "${DEPLOY_BIN}" ]; then
  PREVIOUS=$(readlink -f $DEPLOY_BIN)
  echo "Current binary is ${PREVIOUS}, saved for rollback."
else
  echo "No symbolic link found, no previous binary to backup."
  PREVIOUS=""
fi

rollback_deployment() {
  if [ -n "$PREVIOUS" ]; then
    echo "Rolling back to previous binary: ${PREVIOUS}"
    ln -sfn "${PREVIOUS}" "${DEPLOY_BIN}"
  else
    echo "No previous binary to roll back to."
  fi

  sleep 10

  echo "Restarting ${SERVICE_NAME}..."
  sudo systemctl restart "${SERVICE_NAME}"

  echo "Rollback completed."
}

# Promote the binary
echo "Promoting ${BINARY_NAME} to ${DEPLOY_BIN}..."
ln -sf "${RELEASES_DIR}/${BINARY_NAME}" "${DEPLOY_BIN}"

WAIT_TIME=10
HEALTH_CHECK_TIMEOUT=300  # 5 minutes

health_check() {
  local port=$1
  local timeout=$HEALTH_CHECK_TIMEOUT
  echo "Performing health check on port ${port}..."

  while [ $timeout -gt 0 ]; do
    if curl -f -s --connect-timeout 5 "http://localhost:${port}/actuator/health" >/dev/null 2>&1 || \
       curl -f -s --connect-timeout 5 "http://localhost:${port}/" >/dev/null 2>&1; then
      echo "✅ Port ${port} is healthy and responding"
      return 0
    fi
    echo "⏳ Waiting for port ${port} to become healthy... (${timeout}s remaining)"
    sleep 5
    timeout=$((timeout - 5))
  done

  echo "❌ Health check failed for port ${port}"
  return 1
}

echo "🔄 Restarting ${SERVICE_NAME}..."
if ! sudo systemctl restart "${SERVICE_NAME}"; then
  echo "❌ Error: Failed to restart ${SERVICE_NAME}. Rolling back deployment."
  rollback_deployment
  exit 1
fi

echo "⏳ Waiting for ${SERVICE_NAME} to initialize..."
sleep $WAIT_TIME

if ! systemctl is-active --quiet "${SERVICE_NAME}"; then
  echo "❌ Error: ${SERVICE_NAME} failed to start correctly. Rolling back deployment."
  rollback_deployment
  exit 1
fi

if ! health_check $PORT; then
  echo "❌ Error: Health check failed for ${SERVICE_NAME}. Rolling back deployment."
  rollback_deployment
  exit 1
fi

echo "✅ ${SERVICE_NAME} restarted and is healthy."
echo "Deployment completed successfully."
