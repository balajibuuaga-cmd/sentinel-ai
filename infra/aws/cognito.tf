resource "aws_cognito_user_pool" "main" {
  name = local.name

  deletion_protection = var.enable_deletion_protection ? "ACTIVE" : "INACTIVE"

  username_attributes      = ["email"]
  auto_verified_attributes = ["email"]

  password_policy {
    minimum_length                   = 12
    require_lowercase                = true
    require_numbers                  = true
    require_symbols                  = true
    require_uppercase                = true
    temporary_password_validity_days = 7
  }

  admin_create_user_config {
    allow_admin_create_user_only = true
  }

  account_recovery_setting {
    recovery_mechanism {
      name     = "verified_email"
      priority = 1
    }
  }

  software_token_mfa_configuration {
    enabled = true
  }

  schema {
    name                = "role"
    attribute_data_type = "String"
    mutable             = true
    required            = false

    string_attribute_constraints {
      min_length = 1
      max_length = 32
    }
  }

  schema {
    name                = "tenant_id"
    attribute_data_type = "String"
    mutable             = true
    required            = false

    string_attribute_constraints {
      min_length = 1
      max_length = 80
    }
  }

  schema {
    name                = "organization_name"
    attribute_data_type = "String"
    mutable             = true
    required            = false

    string_attribute_constraints {
      min_length = 1
      max_length = 120
    }
  }

  tags = {
    Name = "${local.name}-users"
  }
}

resource "aws_cognito_user_pool_client" "app" {
  name         = "${local.name}-app"
  user_pool_id = aws_cognito_user_pool.main.id

  generate_secret                      = true
  prevent_user_existence_errors        = "ENABLED"
  enable_token_revocation              = true
  allowed_oauth_flows_user_pool_client = true
  allowed_oauth_flows                  = ["code"]
  allowed_oauth_scopes                 = ["email", "openid", "profile"]
  callback_urls                        = ["${local.app_base_url}/"]
  logout_urls                          = ["${local.app_base_url}/"]
  supported_identity_providers         = ["COGNITO"]
  read_attributes                      = ["email", "profile", "custom:role", "custom:tenant_id", "custom:organization_name"]
  write_attributes                     = ["email", "profile", "custom:role", "custom:tenant_id", "custom:organization_name"]

  access_token_validity  = 60
  id_token_validity      = 60
  refresh_token_validity = 30

  token_validity_units {
    access_token  = "minutes"
    id_token      = "minutes"
    refresh_token = "days"
  }
}

resource "aws_cognito_user_pool_domain" "app" {
  domain       = local.cognito_domain_prefix
  user_pool_id = aws_cognito_user_pool.main.id
}
