#!/usr/bin/env bash
#
# Create security groups for ALB, Server, Consumer, and RabbitMQ.
# Set VPC_ID and AWS_REGION before running.
#
# Example:
#   export VPC_ID=vpc-0abc123def
#   export AWS_REGION=us-east-1
#   ./create-security-groups.sh
#

set -e

AWS_REGION=${AWS_REGION:-us-east-1}
ALB_SG_NAME="chat-ws-alb-sg"
SERVER_SG_NAME="chat-ws-server-sg"
CONSUMER_SG_NAME="chat-ws-consumer-sg"
RABBITMQ_SG_NAME="chat-ws-rabbitmq-sg"

if [ -z "$VPC_ID" ]; then
  echo "Error: VPC_ID is not set. Example: export VPC_ID=vpc-0abc123def"
  exit 1
fi

echo "Using VPC_ID=$VPC_ID AWS_REGION=$AWS_REGION"
echo ""

# 1. ALB security group
echo "Creating ALB security group: $ALB_SG_NAME ..."
ALB_SG_ID=$(aws ec2 create-security-group \
  --group-name "$ALB_SG_NAME" \
  --description "ALB for WebSocket chat servers" \
  --vpc-id "$VPC_ID" \
  --region "$AWS_REGION" \
  --query 'GroupId' --output text 2>/dev/null) || {
  echo "ALB SG may already exist, looking up by name..."
  ALB_SG_ID=$(aws ec2 describe-security-groups \
    --filters "Name=group-name,Values=$ALB_SG_NAME" "Name=vpc-id,Values=$VPC_ID" \
    --region "$AWS_REGION" --query 'SecurityGroups[0].GroupId' --output text)
}

aws ec2 authorize-security-group-ingress \
  --group-id "$ALB_SG_ID" \
  --protocol tcp --port 80 --cidr 0.0.0.0/0 \
  --region "$AWS_REGION" 2>/dev/null || true

echo "ALB_SG_ID=$ALB_SG_ID"
echo ""

# 2. Server security group (inbound 8080 from ALB only)
echo "Creating Server security group: $SERVER_SG_NAME ..."
SERVER_SG_ID=$(aws ec2 create-security-group \
  --group-name "$SERVER_SG_NAME" \
  --description "server-v2 EC2; allow ALB on 8080" \
  --vpc-id "$VPC_ID" \
  --region "$AWS_REGION" \
  --query 'GroupId' --output text 2>/dev/null) || {
  echo "Server SG may already exist, looking up by name..."
  SERVER_SG_ID=$(aws ec2 describe-security-groups \
    --filters "Name=group-name,Values=$SERVER_SG_NAME" "Name=vpc-id,Values=$VPC_ID" \
    --region "$AWS_REGION" --query 'SecurityGroups[0].GroupId' --output text)
}

aws ec2 authorize-security-group-ingress \
  --group-id "$SERVER_SG_ID" \
  --protocol tcp --port 8080 --source-group "$ALB_SG_ID" \
  --region "$AWS_REGION" 2>/dev/null || true

echo "SERVER_SG_ID=$SERVER_SG_ID"
echo ""

# 3. Consumer security group (inbound 8081 from 0.0.0.0/0)
echo "Creating Consumer security group: $CONSUMER_SG_NAME ..."
CONSUMER_SG_ID=$(aws ec2 create-security-group \
  --group-name "$CONSUMER_SG_NAME" \
  --description "Consumer app EC2; WebSocket/HTTP on 8081" \
  --vpc-id "$VPC_ID" \
  --region "$AWS_REGION" \
  --query 'GroupId' --output text 2>/dev/null) || {
  echo "Consumer SG may already exist, looking up by name..."
  CONSUMER_SG_ID=$(aws ec2 describe-security-groups \
    --filters "Name=group-name,Values=$CONSUMER_SG_NAME" "Name=vpc-id,Values=$VPC_ID" \
    --region "$AWS_REGION" --query 'SecurityGroups[0].GroupId' --output text)
}
aws ec2 authorize-security-group-ingress \
  --group-id "$CONSUMER_SG_ID" \
  --protocol tcp --port 8081 --cidr 0.0.0.0/0 \
  --region "$AWS_REGION" 2>/dev/null || true
echo "CONSUMER_SG_ID=$CONSUMER_SG_ID"
echo ""

# 4. RabbitMQ security group (inbound 5672 from Server-SG and Consumer-SG)
echo "Creating RabbitMQ security group: $RABBITMQ_SG_NAME ..."
RABBITMQ_SG_ID=$(aws ec2 create-security-group \
  --group-name "$RABBITMQ_SG_NAME" \
  --description "RabbitMQ EC2; AMQP 5672 from Server and Consumer" \
  --vpc-id "$VPC_ID" \
  --region "$AWS_REGION" \
  --query 'GroupId' --output text 2>/dev/null) || {
  echo "RabbitMQ SG may already exist, looking up by name..."
  RABBITMQ_SG_ID=$(aws ec2 describe-security-groups \
    --filters "Name=group-name,Values=$RABBITMQ_SG_NAME" "Name=vpc-id,Values=$VPC_ID" \
    --region "$AWS_REGION" --query 'SecurityGroups[0].GroupId' --output text)
}
aws ec2 authorize-security-group-ingress \
  --group-id "$RABBITMQ_SG_ID" \
  --protocol tcp --port 5672 --source-group "$SERVER_SG_ID" \
  --region "$AWS_REGION" 2>/dev/null || true
aws ec2 authorize-security-group-ingress \
  --group-id "$RABBITMQ_SG_ID" \
  --protocol tcp --port 5672 --source-group "$CONSUMER_SG_ID" \
  --region "$AWS_REGION" 2>/dev/null || true
echo "RABBITMQ_SG_ID=$RABBITMQ_SG_ID"
echo ""
echo "--- Security groups ready. Save for later: ---"
echo "export ALB_SG_ID=$ALB_SG_ID"
echo "export SERVER_SG_ID=$SERVER_SG_ID"
echo "export CONSUMER_SG_ID=$CONSUMER_SG_ID"
echo "export RABBITMQ_SG_ID=$RABBITMQ_SG_ID"
