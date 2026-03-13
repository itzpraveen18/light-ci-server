terraform {
  required_version = ">= 1.3"
  required_providers {
    aws = {
      source  = "hashicorp/aws"
      version = "~> 5.0"
    }
  }
}

provider "aws" {
  region = var.aws_region
}

# ── Data: Latest Amazon Linux 2023 AMI ────────────────────────────────────────
data "aws_ami" "amazon_linux_2023" {
  most_recent = true
  owners      = ["amazon"]

  filter {
    name   = "name"
    values = ["al2023-ami-*-x86_64"]
  }

  filter {
    name   = "virtualization-type"
    values = ["hvm"]
  }
}

# ── Data: Default VPC ──────────────────────────────────────────────────────────
data "aws_vpc" "default" {
  default = true
}

# ── Data: First available default subnet ──────────────────────────────────────
data "aws_subnets" "default" {
  filter {
    name   = "vpc-id"
    values = [data.aws_vpc.default.id]
  }
}

# ── Security Group ─────────────────────────────────────────────────────────────
resource "aws_security_group" "lightci_sg" {
  name        = "${var.app_name}-sg"
  description = "Allow SSH and application traffic for LightCI"
  vpc_id      = data.aws_vpc.default.id

  # SSH access
  ingress {
    description = "SSH"
    from_port   = 22
    to_port     = 22
    protocol    = "tcp"
    cidr_blocks = ["0.0.0.0/0"]
  }

  # Application access
  ingress {
    description = "LightCI App"
    from_port   = var.app_port
    to_port     = var.app_port
    protocol    = "tcp"
    cidr_blocks = ["0.0.0.0/0"]
  }

  # Allow all outbound
  egress {
    from_port   = 0
    to_port     = 0
    protocol    = "-1"
    cidr_blocks = ["0.0.0.0/0"]
  }

  tags = {
    Name    = "${var.app_name}-sg"
    Project = var.app_name
  }
}

# ── EC2 Instance ───────────────────────────────────────────────────────────────
resource "aws_instance" "lightci" {
  ami                         = data.aws_ami.amazon_linux_2023.id
  instance_type               = var.instance_type
  key_name                    = var.key_pair_name
  subnet_id                   = tolist(data.aws_subnets.default.ids)[0]
  vpc_security_group_ids      = [aws_security_group.lightci_sg.id]
  associate_public_ip_address = true

  # Bootstrap: install Docker on first boot
  user_data = <<-EOF
    #!/bin/bash
    set -e
    yum update -y
    yum install -y docker
    systemctl start docker
    systemctl enable docker
    usermod -aG docker ec2-user
    echo "Docker installed successfully" >> /var/log/user-data.log
  EOF

  tags = {
    Name    = var.app_name
    Project = var.app_name
  }
}
