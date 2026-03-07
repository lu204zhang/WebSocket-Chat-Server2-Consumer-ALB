#!/usr/bin/env bash
#
# Create ALB, Target Group, and HTTP Listener for WebSocket chat.
# Prerequisites: VPC_ID, SUBNET_1, SUBNET_2, ALB_SG_ID (from create-security-groups.sh).
#
# Usage:
#   export VPC_ID=vpc-xxx SUBNET_1=subnet-xxx SUBNET_2=subnet-yyy ALB_SG_ID=sg-xxx
#   export AWS_REGION=us-east-1
#   ./create-alb-and-tg.sh
#

set -e

AWS_REGION=${AWS_REGION:-us-east-1}

for v in VPC_ID SUBNET_1 SUBNET_2 ALB_SG_ID; do
  if [ -z "${!v}" ]; then
    echo "Error: $v is not set."
    exit 1
  fi
done

echo "Creating Target Group..."
TG_ARN=$(aws elbv2 create-target-group \
  --name chat-ws-tg \
  --protocol HTTP \
  --port 8080 \
  --vpc-id "$VPC_ID" \
  --health-check-enabled \
  --health-check-path /health \
  --health-check-interval-seconds 30 \
  --health-check-timeout-seconds 5 \
  --healthy-threshold-count 2 \
  --unhealthy-threshold-count 3 \
  --matcher HttpCode=200 \
  --target-type instance \
  --region "$AWS_REGION" \
  --query 'TargetGroups[0].TargetGroupArn' --output text)

aws elbv2 modify-target-group-attributes \
  --target-group-arn "$TG_ARN" \
  --attributes Key=deregistration_delay.timeout_seconds,Value=300 \
  --region "$AWS_REGION"

aws elbv2 modify-target-group-attributes \
  --target-group-arn "$TG_ARN" \
  --attributes \
    Key=stickiness.enabled,Value=true \
    Key=stickiness.type,Value=lb_cookie \
    Key=stickiness.lb_cookie.duration_seconds,Value=86400 \
  --region "$AWS_REGION"

echo "TARGET_GROUP_ARN=$TG_ARN"

echo "Creating ALB..."
ALB_ARN=$(aws elbv2 create-load-balancer \
  --name chat-ws-alb \
  --type application \
  --scheme internet-facing \
  --subnets "$SUBNET_1" "$SUBNET_2" \
  --security-groups "$ALB_SG_ID" \
  --region "$AWS_REGION" \
  --query 'LoadBalancers[0].LoadBalancerArn' --output text)

aws elbv2 modify-load-balancer-attributes \
  --load-balancer-arn "$ALB_ARN" \
  --attributes Key=idle_timeout.timeout_seconds,Value=3600 \
  --region "$AWS_REGION"

echo "ALB_ARN=$ALB_ARN"

echo "Creating Listener (HTTP:80 -> Target Group)..."
aws elbv2 create-listener \
  --load-balancer-arn "$ALB_ARN" \
  --protocol HTTP \
  --port 80 \
  --default-actions Type=forward,TargetGroupArn="$TG_ARN" \
  --region "$AWS_REGION"

echo "Done. Save for ASG: export TARGET_GROUP_ARN=$TG_ARN"
