data "aws_caller_identity" "current" {}

data "aws_iam_policy_document" "kms" {
  statement {
    sid = "EnableRootAccountAdministration"

    principals {
      type        = "AWS"
      identifiers = ["arn:aws:iam::${data.aws_caller_identity.current.account_id}:root"]
    }

    actions   = ["kms:*"]
    resources = ["*"]
  }

  statement {
    sid = "AllowAwsServices"

    principals {
      type = "Service"
      identifiers = [
        "logs.${var.aws_region}.amazonaws.com",
        "rds.amazonaws.com",
        "secretsmanager.amazonaws.com"
      ]
    }

    actions = [
      "kms:Decrypt",
      "kms:DescribeKey",
      "kms:Encrypt",
      "kms:GenerateDataKey",
      "kms:GenerateDataKeyWithoutPlaintext",
      "kms:ReEncryptFrom",
      "kms:ReEncryptTo"
    ]

    resources = ["*"]
  }
}

resource "aws_kms_key" "main" {
  description             = "Sentinel AI ${var.environment} encryption key"
  deletion_window_in_days = 30
  enable_key_rotation     = true
  policy                  = data.aws_iam_policy_document.kms.json
}

resource "aws_kms_alias" "main" {
  name          = "alias/${local.name}"
  target_key_id = aws_kms_key.main.key_id
}

resource "aws_security_group" "alb" {
  name        = "${local.name}-alb"
  description = "Public ingress to Sentinel AI ALB"
  vpc_id      = aws_vpc.main.id

  tags = {
    Name = "${local.name}-alb"
  }
}

resource "aws_security_group" "app" {
  name        = "${local.name}-app"
  description = "Sentinel AI ECS service"
  vpc_id      = aws_vpc.main.id

  tags = {
    Name = "${local.name}-app"
  }
}

resource "aws_security_group" "db" {
  name        = "${local.name}-db"
  description = "Private RDS PostgreSQL"
  vpc_id      = aws_vpc.main.id

  tags = {
    Name = "${local.name}-db"
  }
}

resource "aws_security_group_rule" "alb_http_ingress" {
  type              = "ingress"
  security_group_id = aws_security_group.alb.id
  description       = "HTTP"
  from_port         = 80
  to_port           = 80
  protocol          = "tcp"
  cidr_blocks       = var.allowed_ingress_cidrs
}

resource "aws_security_group_rule" "alb_https_ingress" {
  type              = "ingress"
  security_group_id = aws_security_group.alb.id
  description       = "HTTPS"
  from_port         = 443
  to_port           = 443
  protocol          = "tcp"
  cidr_blocks       = var.allowed_ingress_cidrs
}

resource "aws_security_group_rule" "alb_to_app" {
  type                     = "egress"
  security_group_id        = aws_security_group.alb.id
  description              = "ALB to ECS"
  from_port                = var.app_port
  to_port                  = var.app_port
  protocol                 = "tcp"
  source_security_group_id = aws_security_group.app.id
}

resource "aws_security_group_rule" "app_from_alb" {
  type                     = "ingress"
  security_group_id        = aws_security_group.app.id
  description              = "ALB to app"
  from_port                = var.app_port
  to_port                  = var.app_port
  protocol                 = "tcp"
  source_security_group_id = aws_security_group.alb.id
}

resource "aws_security_group_rule" "app_https_egress" {
  type              = "egress"
  security_group_id = aws_security_group.app.id
  description       = "HTTPS outbound"
  from_port         = 443
  to_port           = 443
  protocol          = "tcp"
  cidr_blocks       = ["0.0.0.0/0"]
}

resource "aws_security_group_rule" "app_to_db" {
  type                     = "egress"
  security_group_id        = aws_security_group.app.id
  description              = "PostgreSQL to RDS"
  from_port                = 5432
  to_port                  = 5432
  protocol                 = "tcp"
  source_security_group_id = aws_security_group.db.id
}

resource "aws_security_group_rule" "db_from_app" {
  type                     = "ingress"
  security_group_id        = aws_security_group.db.id
  description              = "App to PostgreSQL"
  from_port                = 5432
  to_port                  = 5432
  protocol                 = "tcp"
  source_security_group_id = aws_security_group.app.id
}
