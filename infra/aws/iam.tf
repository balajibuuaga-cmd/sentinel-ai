data "aws_iam_policy_document" "ecs_task_assume_role" {
  statement {
    actions = ["sts:AssumeRole"]

    principals {
      type        = "Service"
      identifiers = ["ecs-tasks.amazonaws.com"]
    }
  }
}

resource "aws_iam_role" "task_execution" {
  name               = "${local.name}-task-execution"
  assume_role_policy = data.aws_iam_policy_document.ecs_task_assume_role.json
}

resource "aws_iam_role_policy_attachment" "task_execution" {
  role       = aws_iam_role.task_execution.name
  policy_arn = "arn:aws:iam::aws:policy/service-role/AmazonECSTaskExecutionRolePolicy"
}

data "aws_iam_policy_document" "task_execution_secrets" {
  statement {
    actions = [
      "secretsmanager:GetSecretValue",
      "kms:Decrypt"
    ]

    resources = [
      aws_secretsmanager_secret.app.arn,
      aws_kms_key.main.arn
    ]
  }
}

resource "aws_iam_policy" "task_execution_secrets" {
  name   = "${local.name}-task-execution-secrets"
  policy = data.aws_iam_policy_document.task_execution_secrets.json
}

resource "aws_iam_role_policy_attachment" "task_execution_secrets" {
  role       = aws_iam_role.task_execution.name
  policy_arn = aws_iam_policy.task_execution_secrets.arn
}

resource "aws_iam_role" "task" {
  name               = "${local.name}-task"
  assume_role_policy = data.aws_iam_policy_document.ecs_task_assume_role.json
}

data "aws_iam_policy_document" "task_runtime" {
  statement {
    actions = [
      "cloudwatch:PutMetricData"
    ]

    resources = ["*"]

    condition {
      test     = "StringEquals"
      variable = "cloudwatch:namespace"
      values   = ["SentinelAI"]
    }
  }
}

resource "aws_iam_policy" "task_runtime" {
  name   = "${local.name}-task-runtime"
  policy = data.aws_iam_policy_document.task_runtime.json
}

resource "aws_iam_role_policy_attachment" "task_runtime" {
  role       = aws_iam_role.task.name
  policy_arn = aws_iam_policy.task_runtime.arn
}
