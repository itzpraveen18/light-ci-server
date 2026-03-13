variable "aws_region" {
  description = "AWS region to deploy the EC2 instance"
  type        = string
  default     = "us-east-1"
}

variable "instance_type" {
  description = "EC2 instance type (t2.micro is free tier eligible)"
  type        = string
  default     = "t2.micro"
}

variable "key_pair_name" {
  description = "Name of your existing EC2 key pair for SSH access"
  type        = string
  # No default — must be provided by developer
}

variable "app_port" {
  description = "Application port exposed by the container"
  type        = number
  default     = 8080
}

variable "app_name" {
  description = "Name tag applied to AWS resources"
  type        = string
  default     = "light-ci-server"
}
