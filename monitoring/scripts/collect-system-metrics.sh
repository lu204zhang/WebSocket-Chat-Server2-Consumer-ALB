#!/usr/bin/env bash
#
# Collect EC2 system metrics from CloudWatch: CPUUtilization, NetworkIn, NetworkOut
# Memory requires CloudWatch Agent (not included by default)
# Uses period=300 (5 min) because EC2 basic monitoring only has 5-minute granularity.
#
# Env: INSTANCE_IDS (comma-separated), AWS_REGION, DURATION_MIN
#
# Output: Per-instance summary
#

set -e

AWS_REGION=${AWS_REGION:-us-east-1}
DURATION_MIN=${DURATION_MIN:-15}

if [ -z "$INSTANCE_IDS" ]; then
  echo "Error: Set INSTANCE_IDS (comma-separated)" >&2
  echo "Example: export INSTANCE_IDS=i-0abc123,i-0def456" >&2
  exit 1
fi

END_OFFSET_MIN=5
START_OFFSET_MIN=$((DURATION_MIN + END_OFFSET_MIN))
START_TIME=$(date -u -d "-${START_OFFSET_MIN} minutes" +"%Y-%m-%dT%H:%M:%SZ" 2>/dev/null || date -u -v-${START_OFFSET_MIN}M +"%Y-%m-%dT%H:%M:%SZ" 2>/dev/null || date -u -d "@$(($(date +%s) - START_OFFSET_MIN * 60))" +"%Y-%m-%dT%H:%M:%SZ" 2>/dev/null)
END_TIME=$(date -u -d "-${END_OFFSET_MIN} minutes" +"%Y-%m-%dT%H:%M:%SZ" 2>/dev/null || date -u -v-${END_OFFSET_MIN}M +"%Y-%m-%dT%H:%M:%SZ" 2>/dev/null || date -u -d "@$(($(date +%s) - END_OFFSET_MIN * 60))" +"%Y-%m-%dT%H:%M:%SZ" 2>/dev/null)

if [ -z "$START_TIME" ] || [ -z "$END_TIME" ]; then
  echo "Error: Could not compute START_TIME/END_TIME. Check 'date' command." >&2
  exit 1
fi

IFS=',' read -ra IDS <<< "$INSTANCE_IDS"

echo "=== EC2 System Metrics ($DURATION_MIN min) ==="
echo "Instances: ${INSTANCE_IDS}"
echo "Region: $AWS_REGION"
echo "Time range: $START_TIME to $END_TIME (UTC)"
echo ""

for iid in "${IDS[@]}"; do
  iid=$(echo "$iid" | xargs)
  [ -z "$iid" ] && continue
  echo "--- Instance: $iid ---"
  echo "CPUUtilization (avg %):"
  aws cloudwatch get-metric-statistics \
    --namespace AWS/EC2 \
    --metric-name CPUUtilization \
    --dimensions Name=InstanceId,Value="$iid" \
    --start-time "$START_TIME" \
    --end-time "$END_TIME" \
    --period 300 \
    --statistics Average \
    --region "$AWS_REGION" \
    --query 'Datapoints | sort_by(@, &Timestamp) | [-1].Average' \
    --output text 2>/dev/null || echo "N/A"

  echo "NetworkIn (sum bytes):"
  aws cloudwatch get-metric-statistics \
    --namespace AWS/EC2 \
    --metric-name NetworkIn \
    --dimensions Name=InstanceId,Value="$iid" \
    --start-time "$START_TIME" \
    --end-time "$END_TIME" \
    --period 300 \
    --statistics Sum \
    --region "$AWS_REGION" \
    --query 'Datapoints | sort_by(@, &Timestamp) | [-1].Sum' \
    --output text 2>/dev/null || echo "N/A"

  echo "NetworkOut (sum bytes):"
  aws cloudwatch get-metric-statistics \
    --namespace AWS/EC2 \
    --metric-name NetworkOut \
    --dimensions Name=InstanceId,Value="$iid" \
    --start-time "$START_TIME" \
    --end-time "$END_TIME" \
    --period 300 \
    --statistics Sum \
    --region "$AWS_REGION" \
    --query 'Datapoints | sort_by(@, &Timestamp) | [-1].Sum' \
    --output text 2>/dev/null || echo "N/A"

  echo "Memory: N/A (requires CloudWatch Agent)"
  echo ""
done
