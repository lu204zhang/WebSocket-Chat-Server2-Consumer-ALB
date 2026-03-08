#!/usr/bin/env bash
#
# Collect ALB metrics from CloudWatch: RequestCount, TargetResponseTime, HealthyHostCount
#
# Env: ALB_ARN or ALB_NAME, AWS_REGION (default us-east-1), DURATION_MIN (default 15)
#
# Output: Summary to stdout
#

set -e

AWS_REGION=${AWS_REGION:-us-east-1}
DURATION_MIN=${DURATION_MIN:-15}

if [ -z "$ALB_ARN" ] && [ -z "$ALB_NAME" ]; then
  echo "Error: Set ALB_ARN or ALB_NAME" >&2
  echo "Example: export ALB_NAME=chat-ws-alb" >&2
  exit 1
fi

if [ -z "$ALB_ARN" ]; then
  ALB_ARN=$(aws elbv2 describe-load-balancers --names "$ALB_NAME" --region "$AWS_REGION" \
    --query 'LoadBalancers[0].LoadBalancerArn' --output text 2>/dev/null || true)
  if [ -z "$ALB_ARN" ] || [ "$ALB_ARN" = "None" ]; then
    echo "Error: Could not find ALB '$ALB_NAME'" >&2
    exit 1
  fi
fi

# Extract load balancer ID for CloudWatch dimension (app/name/id)
ALB_SUFFIX=$(echo "$ALB_ARN" | sed 's|.*loadbalancer/||')

END_OFFSET_MIN=5
START_OFFSET_MIN=$((DURATION_MIN + END_OFFSET_MIN))
START_TIME=$(date -u -d "-${START_OFFSET_MIN} minutes" +"%Y-%m-%dT%H:%M:%SZ" 2>/dev/null || date -u -v-${START_OFFSET_MIN}M +"%Y-%m-%dT%H:%M:%SZ" 2>/dev/null || date -u -d "@$(($(date +%s) - START_OFFSET_MIN * 60))" +"%Y-%m-%dT%H:%M:%SZ" 2>/dev/null)
END_TIME=$(date -u -d "-${END_OFFSET_MIN} minutes" +"%Y-%m-%dT%H:%M:%SZ" 2>/dev/null || date -u -v-${END_OFFSET_MIN}M +"%Y-%m-%dT%H:%M:%SZ" 2>/dev/null || date -u -d "@$(($(date +%s) - END_OFFSET_MIN * 60))" +"%Y-%m-%dT%H:%M:%SZ" 2>/dev/null)

echo "=== ALB Metrics ($DURATION_MIN min) ==="
echo "ALB: $ALB_ARN"
echo "Region: $AWS_REGION"
echo "Time range: $START_TIME to $END_TIME (UTC)"
echo ""

echo "--- RequestCount ---"
aws cloudwatch get-metric-statistics \
  --namespace AWS/ApplicationELB \
  --metric-name RequestCount \
  --dimensions Name=LoadBalancer,Value="$ALB_SUFFIX" \
  --start-time "$START_TIME" \
  --end-time "$END_TIME" \
  --period 60 \
  --statistics Sum \
  --region "$AWS_REGION" \
  --query 'Datapoints | sort_by(@, &Timestamp) | [-1]' \
  --output table 2>/dev/null || echo "No data"

echo ""
echo "--- TargetResponseTime (avg ms) ---"
aws cloudwatch get-metric-statistics \
  --namespace AWS/ApplicationELB \
  --metric-name TargetResponseTime \
  --dimensions Name=LoadBalancer,Value="$ALB_SUFFIX" \
  --start-time "$START_TIME" \
  --end-time "$END_TIME" \
  --period 60 \
  --statistics Average \
  --region "$AWS_REGION" \
  --query 'Datapoints | sort_by(@, &Timestamp) | [-1]' \
  --output table 2>/dev/null || echo "No data"

echo ""
echo "--- HealthyHostCount ---"

TG_ARN=$(aws elbv2 describe-target-groups --load-balancer-arn "$ALB_ARN" --region "$AWS_REGION" \
  --query 'TargetGroups[0].TargetGroupArn' --output text 2>/dev/null || true)
if [ -n "$TG_ARN" ] && [ "$TG_ARN" != "None" ]; then
  TG_SUFFIX=$(echo "$TG_ARN" | sed 's|.*targetgroup/||')
  aws cloudwatch get-metric-statistics \
    --namespace AWS/ApplicationELB \
    --metric-name HealthyHostCount \
    --dimensions Name=LoadBalancer,Value="$ALB_SUFFIX" Name=TargetGroup,Value="$TG_SUFFIX" \
    --start-time "$START_TIME" \
    --end-time "$END_TIME" \
    --period 60 \
    --statistics Average \
    --region "$AWS_REGION" \
    --query 'Datapoints | sort_by(@, &Timestamp) | [-1]' \
    --output table 2>/dev/null || echo "No data"
else
  echo "No data (TargetGroup required for HealthyHostCount)"
  echo "  Tip: Ensure IAM has elbv2:DescribeTargetGroups and ALB has a target group attached."
fi
