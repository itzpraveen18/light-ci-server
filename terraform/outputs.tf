output "ec2_public_ip" {
  description = "Public IP address of the EC2 instance"
  value       = aws_instance.lightci.public_ip
}

output "ec2_public_dns" {
  description = "Public DNS of the EC2 instance"
  value       = aws_instance.lightci.public_dns
}

output "app_url" {
  description = "URL to access the LightCI application"
  value       = "http://${aws_instance.lightci.public_ip}:8080"
}

output "ssh_command" {
  description = "SSH command to connect to the EC2 instance"
  value       = "ssh -i /path/to/${var.key_pair_name}.pem ec2-user@${aws_instance.lightci.public_ip}"
}

output "security_group_id" {
  description = "ID of the created security group"
  value       = aws_security_group.lightci_sg.id
}
