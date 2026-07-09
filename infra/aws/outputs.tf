output "alb_dns_name" {
  description = "Public load balancer DNS name."
  value       = aws_lb.app.dns_name
}

output "ecs_cluster_name" {
  description = "ECS cluster name."
  value       = aws_ecs_cluster.main.name
}

output "ecs_service_name" {
  description = "ECS service name."
  value       = aws_ecs_service.app.name
}

output "rds_endpoint" {
  description = "Private RDS PostgreSQL endpoint."
  value       = aws_db_instance.postgres.address
}

output "app_secret_arn" {
  description = "Secrets Manager ARN containing app secrets."
  value       = aws_secretsmanager_secret.app.arn
}

output "cognito_user_pool_id" {
  description = "Cognito user pool id for customer auth integration."
  value       = aws_cognito_user_pool.main.id
}

output "cognito_hosted_ui_base_url" {
  description = "Cognito hosted UI base URL."
  value       = "https://${aws_cognito_user_pool_domain.app.domain}.auth.${var.aws_region}.amazoncognito.com"
}

output "waf_web_acl_arn" {
  description = "Regional WAF web ACL ARN."
  value       = aws_wafv2_web_acl.app.arn
}
