resource "aws_db_subnet_group" "main" {
  name       = local.name
  subnet_ids = aws_subnet.private_data[*].id

  tags = {
    Name = "${local.name}-db"
  }
}

resource "aws_db_instance" "postgres" {
  identifier = local.name

  engine         = "postgres"
  engine_version = "16"
  instance_class = var.db_instance_class

  db_name  = var.db_name
  username = var.db_username
  password = random_password.db_password.result

  allocated_storage     = var.db_allocated_storage
  max_allocated_storage = max(var.db_allocated_storage * 5, 100)
  storage_type          = "gp3"
  storage_encrypted     = true
  kms_key_id            = aws_kms_key.main.arn

  db_subnet_group_name   = aws_db_subnet_group.main.name
  vpc_security_group_ids = [aws_security_group.db.id]
  publicly_accessible    = false

  backup_retention_period = var.db_backup_retention_days
  backup_window           = "08:00-09:00"
  maintenance_window      = "sun:09:00-sun:10:00"

  deletion_protection = var.enable_deletion_protection
  skip_final_snapshot = !var.enable_deletion_protection
  final_snapshot_identifier = var.enable_deletion_protection ? null : "${local.name}-final"

  auto_minor_version_upgrade = true
  copy_tags_to_snapshot      = true

  tags = {
    Name = "${local.name}-postgres"
  }
}
