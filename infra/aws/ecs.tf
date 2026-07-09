resource "aws_cloudwatch_log_group" "app" {
  name              = "/aws/ecs/${local.name}"
  retention_in_days = var.log_retention_days
  kms_key_id        = aws_kms_key.main.arn
}

resource "aws_ecs_cluster" "main" {
  name = local.name

  setting {
    name  = "containerInsights"
    value = "enabled"
  }
}

resource "aws_lb" "app" {
  name               = local.name
  load_balancer_type = "application"
  internal           = false
  security_groups    = [aws_security_group.alb.id]
  subnets            = aws_subnet.public[*].id

  enable_deletion_protection = var.enable_deletion_protection
  drop_invalid_header_fields = true
}

resource "aws_lb_target_group" "app" {
  name        = local.name
  port        = var.app_port
  protocol    = "HTTP"
  target_type = "ip"
  vpc_id      = aws_vpc.main.id

  health_check {
    enabled             = true
    healthy_threshold   = 2
    interval            = 30
    matcher             = "200"
    path                = "/actuator/health"
    port                = "traffic-port"
    protocol            = "HTTP"
    timeout             = 5
    unhealthy_threshold = 3
  }
}

resource "aws_lb_listener" "http" {
  load_balancer_arn = aws_lb.app.arn
  port              = 80
  protocol          = "HTTP"

  dynamic "default_action" {
    for_each = var.certificate_arn == "" ? [1] : []

    content {
      type             = "forward"
      target_group_arn = aws_lb_target_group.app.arn
    }
  }

  dynamic "default_action" {
    for_each = var.certificate_arn == "" ? [] : [1]

    content {
      type = "redirect"

      redirect {
        port        = "443"
        protocol    = "HTTPS"
        status_code = "HTTP_301"
      }
    }
  }
}

resource "aws_lb_listener" "https" {
  count = var.certificate_arn == "" ? 0 : 1

  load_balancer_arn = aws_lb.app.arn
  port              = 443
  protocol          = "HTTPS"
  certificate_arn   = var.certificate_arn
  ssl_policy        = "ELBSecurityPolicy-TLS13-1-2-2021-06"

  default_action {
    type             = "forward"
    target_group_arn = aws_lb_target_group.app.arn
  }
}

resource "aws_ecs_task_definition" "app" {
  family                   = local.name
  requires_compatibilities = ["FARGATE"]
  network_mode             = "awsvpc"
  cpu                      = var.task_cpu
  memory                   = var.task_memory
  execution_role_arn       = aws_iam_role.task_execution.arn
  task_role_arn            = aws_iam_role.task.arn

  container_definitions = jsonencode([
    {
      name      = "sentinel-ai"
      image     = var.container_image
      essential = true

      portMappings = [
        {
          containerPort = var.app_port
          hostPort      = var.app_port
          protocol      = "tcp"
        }
      ]

      environment = [
        {
          name  = "PORT"
          value = tostring(var.app_port)
        },
        {
          name  = "SPRING_DATASOURCE_URL"
          value = "jdbc:postgresql://${aws_db_instance.postgres.address}:5432/${var.db_name}"
        },
        {
          name  = "SPRING_DATASOURCE_USERNAME"
          value = var.db_username
        },
        {
          name  = "SPRING_JPA_HIBERNATE_DDL_AUTO"
          value = "validate"
        },
        {
          name  = "SPRING_FLYWAY_ENABLED"
          value = "true"
        },
        {
          name  = "SPRING_FLYWAY_BASELINE_ON_MIGRATE"
          value = "true"
        },
        {
          name  = "SENTINEL_API_ENABLED"
          value = "true"
        },
        {
          name  = "SENTINEL_WORKER_ENABLED"
          value = "false"
        },
        {
          name  = "SENTINEL_REDIS_RATE_LIMIT_ENABLED"
          value = "false"
        },
        {
          name  = "SENTINEL_REDIS_HOST"
          value = "localhost"
        },
        {
          name  = "SENTINEL_AI_PROVIDER"
          value = "deterministic"
        },
        {
          name  = "SENTINEL_AI_MODEL"
          value = "deterministic-chief-engineer-v1"
        },
        {
          name  = "SENTINEL_AI_EXTERNAL_CALLS_ENABLED"
          value = "false"
        },
        {
          name  = "SENTINEL_AUTH_MODE"
          value = "cognito"
        },
        {
          name  = "SENTINEL_COGNITO_ISSUER"
          value = "https://cognito-idp.${var.aws_region}.amazonaws.com/${aws_cognito_user_pool.main.id}"
        },
        {
          name  = "SENTINEL_COGNITO_AUDIENCE"
          value = aws_cognito_user_pool_client.app.id
        },
        {
          name  = "SENTINEL_COGNITO_CLIENT_ID"
          value = aws_cognito_user_pool_client.app.id
        },
        {
          name  = "SENTINEL_COGNITO_HOSTED_UI_BASE_URL"
          value = "https://${aws_cognito_user_pool_domain.app.domain}.auth.${var.aws_region}.amazoncognito.com"
        },
        {
          name  = "SENTINEL_COGNITO_REDIRECT_URI"
          value = "${local.app_base_url}/"
        },
        {
          name  = "SENTINEL_COGNITO_LOGOUT_URI"
          value = "${local.app_base_url}/"
        },
        {
          name  = "SENTINEL_INTEGRATIONS_REAL_EXCHANGE_ENABLED"
          value = tostring(var.integrations_real_exchange_enabled)
        },
        {
          name  = "SENTINEL_GITHUB_CLIENT_ID"
          value = var.github_oauth_client_id
        },
        {
          name  = "SENTINEL_GITHUB_REDIRECT_URI"
          value = "${local.app_base_url}/integrations/github/callback"
        },
        {
          name  = "SENTINEL_JIRA_CLIENT_ID"
          value = var.jira_oauth_client_id
        },
        {
          name  = "SENTINEL_JIRA_REDIRECT_URI"
          value = "${local.app_base_url}/integrations/jira/callback"
        },
        {
          name  = "SENTINEL_JIRA_CLOUD_ID"
          value = var.jira_cloud_id
        },
        {
          name  = "SENTINEL_CI_CLIENT_ID"
          value = var.ci_oauth_client_id
        },
        {
          name  = "SENTINEL_CI_REDIRECT_URI"
          value = "${local.app_base_url}/integrations/ci/callback"
        },
        {
          name  = "SENTINEL_CI_AUTHORIZE_URL"
          value = var.ci_oauth_authorize_url
        },
        {
          name  = "SENTINEL_CI_TOKEN_URL"
          value = var.ci_oauth_token_url
        },
        {
          name  = "SENTINEL_CI_RUNS_URL"
          value = var.ci_runs_url
        }
      ]

      secrets = [
        {
          name      = "SPRING_DATASOURCE_PASSWORD"
          valueFrom = "${aws_secretsmanager_secret.app.arn}:SPRING_DATASOURCE_PASSWORD::"
        },
        {
          name      = "SENTINEL_JWT_SECRET"
          valueFrom = "${aws_secretsmanager_secret.app.arn}:SENTINEL_JWT_SECRET::"
        },
        {
          name      = "SENTINEL_GITHUB_WEBHOOK_SECRET"
          valueFrom = "${aws_secretsmanager_secret.app.arn}:SENTINEL_GITHUB_WEBHOOK_SECRET::"
        },
        {
          name      = "SENTINEL_COGNITO_CLIENT_SECRET"
          valueFrom = "${aws_secretsmanager_secret.app.arn}:SENTINEL_COGNITO_CLIENT_SECRET::"
        },
        {
          name      = "SENTINEL_INTEGRATION_TOKEN_ENCRYPTION_KEY"
          valueFrom = "${aws_secretsmanager_secret.app.arn}:SENTINEL_INTEGRATION_TOKEN_ENCRYPTION_KEY::"
        },
        {
          name      = "SENTINEL_GITHUB_CLIENT_SECRET"
          valueFrom = "${aws_secretsmanager_secret.app.arn}:SENTINEL_GITHUB_CLIENT_SECRET::"
        },
        {
          name      = "SENTINEL_JIRA_CLIENT_SECRET"
          valueFrom = "${aws_secretsmanager_secret.app.arn}:SENTINEL_JIRA_CLIENT_SECRET::"
        },
        {
          name      = "SENTINEL_CI_CLIENT_SECRET"
          valueFrom = "${aws_secretsmanager_secret.app.arn}:SENTINEL_CI_CLIENT_SECRET::"
        }
      ]

      logConfiguration = {
        logDriver = "awslogs"
        options = {
          awslogs-group         = aws_cloudwatch_log_group.app.name
          awslogs-region        = var.aws_region
          awslogs-stream-prefix = "app"
        }
      }
    }
  ])
}

resource "aws_ecs_service" "app" {
  name            = local.name
  cluster         = aws_ecs_cluster.main.id
  task_definition = aws_ecs_task_definition.app.arn
  desired_count   = var.desired_count
  launch_type     = "FARGATE"

  deployment_minimum_healthy_percent = 100
  deployment_maximum_percent         = 200
  enable_execute_command             = true

  network_configuration {
    assign_public_ip = false
    security_groups  = [aws_security_group.app.id]
    subnets          = aws_subnet.private_app[*].id
  }

  load_balancer {
    target_group_arn = aws_lb_target_group.app.arn
    container_name   = "sentinel-ai"
    container_port   = var.app_port
  }

  depends_on = [
    aws_lb_listener.http,
    aws_lb_listener.https,
    aws_db_instance.postgres
  ]
}

resource "aws_ecs_task_definition" "worker" {
  family                   = "${local.name}-worker"
  requires_compatibilities = ["FARGATE"]
  network_mode             = "awsvpc"
  cpu                      = var.task_cpu
  memory                   = var.task_memory
  execution_role_arn       = aws_iam_role.task_execution.arn
  task_role_arn            = aws_iam_role.task.arn

  container_definitions = jsonencode([
    {
      name      = "sentinel-ai-worker"
      image     = var.container_image
      essential = true

      environment = [
        {
          name  = "PORT"
          value = "8091"
        },
        {
          name  = "SPRING_DATASOURCE_URL"
          value = "jdbc:postgresql://${aws_db_instance.postgres.address}:5432/${var.db_name}"
        },
        {
          name  = "SPRING_DATASOURCE_USERNAME"
          value = var.db_username
        },
        {
          name  = "SPRING_JPA_HIBERNATE_DDL_AUTO"
          value = "validate"
        },
        {
          name  = "SPRING_FLYWAY_ENABLED"
          value = "true"
        },
        {
          name  = "SPRING_FLYWAY_BASELINE_ON_MIGRATE"
          value = "true"
        },
        {
          name  = "SENTINEL_API_ENABLED"
          value = "false"
        },
        {
          name  = "SENTINEL_WORKER_ENABLED"
          value = "true"
        },
        {
          name  = "SENTINEL_REDIS_RATE_LIMIT_ENABLED"
          value = "false"
        },
        {
          name  = "SENTINEL_AI_PROVIDER"
          value = "deterministic"
        },
        {
          name  = "SENTINEL_AI_MODEL"
          value = "deterministic-chief-engineer-v1"
        },
        {
          name  = "SENTINEL_AI_EXTERNAL_CALLS_ENABLED"
          value = "false"
        },
        {
          name  = "SENTINEL_AUTH_MODE"
          value = "cognito"
        },
        {
          name  = "SENTINEL_COGNITO_ISSUER"
          value = "https://cognito-idp.${var.aws_region}.amazonaws.com/${aws_cognito_user_pool.main.id}"
        },
        {
          name  = "SENTINEL_COGNITO_AUDIENCE"
          value = aws_cognito_user_pool_client.app.id
        },
        {
          name  = "SENTINEL_COGNITO_CLIENT_ID"
          value = aws_cognito_user_pool_client.app.id
        },
        {
          name  = "SENTINEL_INTEGRATIONS_REAL_EXCHANGE_ENABLED"
          value = tostring(var.integrations_real_exchange_enabled)
        },
        {
          name  = "SENTINEL_GITHUB_CLIENT_ID"
          value = var.github_oauth_client_id
        },
        {
          name  = "SENTINEL_JIRA_CLIENT_ID"
          value = var.jira_oauth_client_id
        },
        {
          name  = "SENTINEL_JIRA_CLOUD_ID"
          value = var.jira_cloud_id
        },
        {
          name  = "SENTINEL_CI_CLIENT_ID"
          value = var.ci_oauth_client_id
        },
        {
          name  = "SENTINEL_CI_RUNS_URL"
          value = var.ci_runs_url
        }
      ]

      secrets = [
        {
          name      = "SPRING_DATASOURCE_PASSWORD"
          valueFrom = "${aws_secretsmanager_secret.app.arn}:SPRING_DATASOURCE_PASSWORD::"
        },
        {
          name      = "SENTINEL_JWT_SECRET"
          valueFrom = "${aws_secretsmanager_secret.app.arn}:SENTINEL_JWT_SECRET::"
        },
        {
          name      = "SENTINEL_GITHUB_WEBHOOK_SECRET"
          valueFrom = "${aws_secretsmanager_secret.app.arn}:SENTINEL_GITHUB_WEBHOOK_SECRET::"
        },
        {
          name      = "SENTINEL_COGNITO_CLIENT_SECRET"
          valueFrom = "${aws_secretsmanager_secret.app.arn}:SENTINEL_COGNITO_CLIENT_SECRET::"
        },
        {
          name      = "SENTINEL_INTEGRATION_TOKEN_ENCRYPTION_KEY"
          valueFrom = "${aws_secretsmanager_secret.app.arn}:SENTINEL_INTEGRATION_TOKEN_ENCRYPTION_KEY::"
        },
        {
          name      = "SENTINEL_GITHUB_CLIENT_SECRET"
          valueFrom = "${aws_secretsmanager_secret.app.arn}:SENTINEL_GITHUB_CLIENT_SECRET::"
        },
        {
          name      = "SENTINEL_JIRA_CLIENT_SECRET"
          valueFrom = "${aws_secretsmanager_secret.app.arn}:SENTINEL_JIRA_CLIENT_SECRET::"
        },
        {
          name      = "SENTINEL_CI_CLIENT_SECRET"
          valueFrom = "${aws_secretsmanager_secret.app.arn}:SENTINEL_CI_CLIENT_SECRET::"
        }
      ]

      logConfiguration = {
        logDriver = "awslogs"
        options = {
          awslogs-group         = aws_cloudwatch_log_group.app.name
          awslogs-region        = var.aws_region
          awslogs-stream-prefix = "worker"
        }
      }
    }
  ])
}

resource "aws_ecs_service" "worker" {
  name            = "${local.name}-worker"
  cluster         = aws_ecs_cluster.main.id
  task_definition = aws_ecs_task_definition.worker.arn
  desired_count   = var.worker_desired_count
  launch_type     = "FARGATE"

  deployment_minimum_healthy_percent = 100
  deployment_maximum_percent         = 200
  enable_execute_command             = true

  network_configuration {
    assign_public_ip = false
    security_groups  = [aws_security_group.app.id]
    subnets          = aws_subnet.private_app[*].id
  }

  depends_on = [
    aws_db_instance.postgres
  ]
}
