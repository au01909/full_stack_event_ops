#!/usr/bin/env bash
# Deploys this docker-compose stack to a single EC2 instance you already own.
# Cheapest legitimate way to run this on AWS without standing up ECS/EKS.
#
# Prereqs (one-time, done by you in the AWS console or CLI):
#   1. An EC2 instance (Amazon Linux 2023, t3.small+) with Docker installed.
#   2. A security group allowing inbound 80 (frontend), 8080 (backend), 22 (ssh).
#   3. An SSH key pair you can use to reach the instance.
#
# Usage:
#   ./deploy/aws-ec2-deploy.sh <ec2-user@host> <path-to-ssh-key.pem>
set -euo pipefail

HOST="${1:?usage: $0 <user@host> <ssh-key-path>}"
KEY="${2:?usage: $0 <user@host> <ssh-key-path>}"

echo "Syncing repo to $HOST..."
rsync -az --exclude 'node_modules' --exclude 'target' --exclude '.git' \
  -e "ssh -i $KEY" ./ "$HOST:~/task-platform/"

echo "Building and starting containers on $HOST..."
ssh -i "$KEY" "$HOST" 'cd ~/task-platform && docker compose up --build -d'

echo "Done. Frontend on http://<ec2-public-ip>, API on :8080, activity-log on :8081."
