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
DEPLOY_BIN="/home/pipeline/production/paymentsms_dev/paymentsms"
SERVICE_NAME="paymentsms_dev"
BINARY_NAME="paymentsms-${COMMIT_HASH}.jar"
declare -a PORTS=("7000" "7001")

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

  # wait to restart the services
  sleep 10

  # Restart all services with the previous binary
  for port in "${PORTS[@]}"; do
    SERVICE="${SERVICE_NAME}@${port}.service"
    echo "Restarting $SERVICE..."
    sudo systemctl restart $SERVICE
  done

  echo "Rollback completed."
}

# Copy the binary to the deployment directory
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

restart_service() {
  local port=$1
  local SERVICE="${SERVICE_NAME}@${port}.service"
  echo "🔄 Rolling restart for ${SERVICE}..."

  # Restart the service
  if ! sudo systemctl restart "$SERVICE"; then
    echo "❌ Error: Failed to restart ${SERVICE}. Rolling back deployment."
    rollback_deployment
    exit 1
  fi

  # Wait a few seconds to allow the service to fully start
  echo "⏳ Waiting for ${SERVICE} to initialize..."
  sleep $WAIT_TIME

  # Check the status of the service
  if ! systemctl is-active --quiet "${SERVICE}"; then
    echo "❌ Error: ${SERVICE} failed to start correctly. Rolling back deployment."
    rollback_deployment
    exit 1
  fi

  # Perform health check
  if ! health_check $port; then
    echo "❌ Error: Health check failed for ${SERVICE}. Rolling back deployment."
    rollback_deployment
    exit 1
  fi

  echo "✅ ${SERVICE} restarted and is healthy."
}

# Rolling deployment: restart services one by one to maintain availability
echo "🚀 Starting rolling deployment..."
for port in "${PORTS[@]}"; do
  echo "📦 Deploying to port ${port}..."
  restart_service $port
  echo "✅ Port ${port} deployment complete."
  
  # Small delay between service restarts
  if [ "${#PORTS[@]}" -gt 1 ]; then
    echo "⏸️  Brief pause before next service..."
    sleep 3
  fi
done

echo "Deployment completed successfully."
