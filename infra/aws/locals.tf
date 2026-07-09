locals {
  name = "${var.project_name}-${var.environment}"
  app_base_url = var.app_base_url == "" ? "https://${aws_lb.app.dns_name}" : trimsuffix(var.app_base_url, "/")
  cognito_domain_prefix = var.cognito_domain_prefix == "" ? local.name : var.cognito_domain_prefix

  tags = {
    Application = "Sentinel AI"
    Environment = var.environment
    ManagedBy   = "Terraform"
  }
}
