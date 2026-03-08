#!/usr/bin/env bash
#
# Collect RabbitMQ queue metrics (room.1 through room.20).
# Uses Management API: /api/queues, /api/overview
#
# Env: RABBITMQ_HOST, RABBITMQ_PORT, RABBITMQ_USER, RABBITMQ_PASS
#      SAMPLE_INTERVAL_SEC (default 10), DURATION_MIN (default 15, 0=single sample)
#      OUTPUT_FILE (optional; if unset, auto: records/rabbitmq-YYYYMMDD-HHMMSS.csv)
#
# Output: CSV to stdout; also appended to OUTPUT_FILE when set or auto-generated
#

set -e

RABBITMQ_HOST=${RABBITMQ_HOST:-localhost}
RABBITMQ_PORT=${RABBITMQ_PORT:-15672}
RABBITMQ_USER=${RABBITMQ_USER:-guest}
RABBITMQ_PASS=${RABBITMQ_PASS:-guest}
SAMPLE_INTERVAL_SEC=${SAMPLE_INTERVAL_SEC:-10}
DURATION_MIN=${DURATION_MIN:-15}

# Auto-generate OUTPUT_FILE with timestamp so runs do not overwrite
if [ -z "$OUTPUT_FILE" ]; then
  RECORDS_DIR="records"
  mkdir -p "$RECORDS_DIR"
  OUTPUT_FILE="${RECORDS_DIR}/rabbitmq-$(date +%Y%m%d-%H%M%S).csv"
  echo "Writing to $OUTPUT_FILE" >&2
fi

BASE_URL="http://${RABBITMQ_HOST}:${RABBITMQ_PORT}/api"
AUTH="${RABBITMQ_USER}:${RABBITMQ_PASS}"

# Room queues: room.1 .. room.20
ROOM_QUEUES="room.1 room.2 room.3 room.4 room.5 room.6 room.7 room.8 room.9 room.10 room.11 room.12 room.13 room.14 room.15 room.16 room.17 room.18 room.19 room.20"

header() {
  echo "timestamp,queue,messages,messages_ready,messages_unacknowledged,publish_rate,deliver_rate"
}

sample_once() {
  local ts=$(date -u +"%Y-%m-%dT%H:%M:%SZ" 2>/dev/null || date -u +"%Y-%m-%d %H:%M:%S")
  local overview
  overview=$(curl -s -u "$AUTH" "${BASE_URL}/overview" 2>/dev/null || echo "{}")
  local publish_rate=""
  local deliver_rate=""
  if command -v jq &>/dev/null; then
    publish_rate=$(echo "$overview" | jq -r '.message_stats.publish_details.rate // 0' 2>/dev/null || echo "0")
    deliver_rate=$(echo "$overview" | jq -r '.message_stats.deliver_details.rate // 0' 2>/dev/null || echo "0")
  fi

  local queues_json
  queues_json=$(curl -s -u "$AUTH" "${BASE_URL}/queues" 2>/dev/null || echo "[]")
  if ! command -v jq &>/dev/null; then
    echo "# Install jq for per-queue metrics. Overview rates: publish=$publish_rate deliver=$deliver_rate" >&2
    local empty_cols=","
    echo "$ts,overview,,$empty_cols,$publish_rate,$deliver_rate"
    return
  fi

  for q in $ROOM_QUEUES; do
    local msg msg_ready msg_unack
    msg=$(echo "$queues_json" | jq -r ".[] | select(.name==\"$q\") | .messages // 0" 2>/dev/null || echo "0")
    msg_ready=$(echo "$queues_json" | jq -r ".[] | select(.name==\"$q\") | .messages_ready // 0" 2>/dev/null || echo "0")
    msg_unack=$(echo "$queues_json" | jq -r ".[] | select(.name==\"$q\") | .messages_unacknowledged // 0" 2>/dev/null || echo "0")
    echo "$ts,$q,$msg,$msg_ready,$msg_unack,$publish_rate,$deliver_rate"
  done
}

if [ -n "$OUTPUT_FILE" ]; then
  header >> "$OUTPUT_FILE"
fi
header

do_sample() {
  if [ -n "$OUTPUT_FILE" ]; then
    sample_once | tee -a "$OUTPUT_FILE"
  else
    sample_once
  fi
}

if [ "$DURATION_MIN" -eq 0 ]; then
  do_sample
  exit 0
fi

end_ts=$(($(date +%s) + DURATION_MIN * 60))
while [ "$(date +%s)" -lt "$end_ts" ]; do
  do_sample
  sleep "$SAMPLE_INTERVAL_SEC"
done
