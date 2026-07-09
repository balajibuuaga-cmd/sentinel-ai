variable "aws_region" {
  description = "AWS region for Sentinel AI."
  type        = string
  default     = "us-east-1"
}

variable "project_name" {
  description = "Short name used for AWS resource names."
  type        = string
  default     = "sentinel-ai"
}

variable "environment" {
  description = "Deployment environment label."
  type        = string
  default     = "prod"
}

variable "container_image" {
  description = "Fully qualified container image for the Sentinel AI Spring Boot app."
  type        = string
}

variable "app_port" {
  description = "Container port exposed by Sentinel AI."
  type        = number
  default     = 8090
}

variable "certificate_arn" {
  description = "ACM certificate ARN for HTTPS on the application load balancer. Leave empty to create HTTP listener only."
  type        = string
  default     = ""
}

variable "app_base_url" {
  description = "Public base URL for Sentinel AI callbacks. Leave empty to use the load balancer DNS name."
  type        = string
  default     = ""
}

variable "cognito_domain_prefix" {
  description = "Globally unique Cognito hosted UI domain prefix. Leave empty to use project-environment name."
  type        = string
  default     = ""
}

variable "integrations_real_exchange_enabled" {
  description = "Enable live OAuth token exchange for GitHub, Jira, and CI providers."
  type        = bool
  default     = false
}

variable "github_oauth_client_id" {
  description = "GitHub OAuth app client id."
  type        = string
  default     = ""
}

variable "github_oauth_client_secret" {
  description = "GitHub OAuth app client secret."
  type        = string
  sensitive   = true
  default     = ""
}

variable "jira_oauth_client_id" {
  description = "Atlassian OAuth app client id."
  type        = string
  default     = ""
}

variable "jira_oauth_client_secret" {
  description = "Atlassian OAuth app client secret."
  type        = string
  sensitive   = true
  default     = ""
}

variable "jira_cloud_id" {
  description = "Atlassian Cloud ID used for Jira REST API sync."
  type        = string
  default     = ""
}

variable "ci_oauth_client_id" {
  description = "Generic CI provider OAuth client id."
  type        = string
  default     = ""
}

variable "ci_oauth_client_secret" {
  description = "Generic CI provider OAuth client secret."
  type        = string
  sensitive   = true
  default     = ""
}

variable "ci_oauth_authorize_url" {
  description = "Generic CI provider OAuth authorization URL."
  type        = string
  default     = ""
}

variable "ci_oauth_token_url" {
  description = "Generic CI provider OAuth token URL."
  type        = string
  default     = ""
}

variable "ci_runs_url" {
  description = "Generic CI provider API URL that returns recent runs as an array or { runs: [...] }."
  type        = string
  default     = ""
}

variable "allowed_ingress_cidrs" {
  description = "CIDR ranges allowed to reach the public load balancer."
  type        = list(string)
  default     = ["0.0.0.0/0"]
}

variable "vpc_cidr" {
  description = "CIDR range for the Sentinel AI VPC."
  type        = string
  default     = "10.42.0.0/16"
}

variable "availability_zone_count" {
  description = "Number of availability zones to use."
  type        = number
  default     = 2

  validation {
    condition     = var.availability_zone_count >= 2 && var.availability_zone_count <= 3
    error_message = "availability_zone_count must be 2 or 3."
  }
}

variable "task_cpu" {
  description = "Fargate task CPU units."
  type        = number
  default     = 1024
}

variable "task_memory" {
  description = "Fargate task memory in MiB."
  type        = number
  default     = 2048
}

variable "desired_count" {
  description = "Desired ECS service task count."
  type        = number
  default     = 2
}

variable "worker_desired_count" {
  description = "Desired ECS worker task count for background jobs and webhook replay."
  type        = number
  default     = 1
}

variable "db_name" {
  description = "PostgreSQL database name."
  type        = string
  default     = "sentinel_ai"
}

variable "db_username" {
  description = "PostgreSQL master username."
  type        = string
  default     = "sentinel"
}

variable "db_instance_class" {
  description = "RDS instance class."
  type        = string
  default     = "db.t4g.micro"
}

variable "db_allocated_storage" {
  description = "Initial RDS allocated storage in GB."
  type        = number
  default     = 20
}

variable "db_backup_retention_days" {
  description = "RDS automated backup retention in days."
  type        = number
  default     = 7
}

variable "log_retention_days" {
  description = "CloudWatch log retention in days."
  type        = number
  default     = 30
}

variable "enable_deletion_protection" {
  description = "Enable deletion protection for production stateful resources."
  type        = bool
  default     = true
}
