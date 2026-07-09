resource "random_password" "db_password" {
  length           = 32
  special          = true
  override_special = "!#$%&*()-_=+[]{}<>:?"
}

resource "random_password" "jwt_secret" {
  length  = 48
  special = false
}

resource "random_password" "github_webhook_secret" {
  length  = 40
  special = false
}

resource "random_password" "integration_token_encryption_key" {
  length  = 48
  special = false
}

resource "aws_secretsmanager_secret" "app" {
  name        = "${local.name}/app"
  description = "Sentinel AI application secrets"
  kms_key_id  = aws_kms_key.main.arn
}

resource "aws_secretsmanager_secret_version" "app" {
  secret_id = aws_secretsmanager_secret.app.id
  secret_string = jsonencode({
    SPRING_DATASOURCE_PASSWORD                = random_password.db_password.result
    SENTINEL_JWT_SECRET                       = random_password.jwt_secret.result
    SENTINEL_GITHUB_WEBHOOK_SECRET            = random_password.github_webhook_secret.result
    SENTINEL_COGNITO_CLIENT_SECRET            = aws_cognito_user_pool_client.app.client_secret
    SENTINEL_INTEGRATION_TOKEN_ENCRYPTION_KEY = random_password.integration_token_encryption_key.result
    SENTINEL_GITHUB_CLIENT_SECRET             = var.github_oauth_client_secret
    SENTINEL_JIRA_CLIENT_SECRET               = var.jira_oauth_client_secret
    SENTINEL_CI_CLIENT_SECRET                 = var.ci_oauth_client_secret
  })
}
