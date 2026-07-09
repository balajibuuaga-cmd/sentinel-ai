data "aws_availability_zones" "available" {
  state = "available"
}

locals {
  availability_zones = slice(data.aws_availability_zones.available.names, 0, var.availability_zone_count)
}

resource "aws_vpc" "main" {
  cidr_block           = var.vpc_cidr
  enable_dns_hostnames = true
  enable_dns_support   = true

  tags = {
    Name = "${local.name}-vpc"
  }
}

resource "aws_internet_gateway" "main" {
  vpc_id = aws_vpc.main.id

  tags = {
    Name = "${local.name}-igw"
  }
}

resource "aws_subnet" "public" {
  count = var.availability_zone_count

  vpc_id                  = aws_vpc.main.id
  availability_zone       = local.availability_zones[count.index]
  cidr_block              = cidrsubnet(var.vpc_cidr, 8, count.index)
  map_public_ip_on_launch = true

  tags = {
    Name = "${local.name}-public-${count.index + 1}"
    Tier = "public"
  }
}

resource "aws_subnet" "private_app" {
  count = var.availability_zone_count

  vpc_id            = aws_vpc.main.id
  availability_zone = local.availability_zones[count.index]
  cidr_block        = cidrsubnet(var.vpc_cidr, 8, count.index + 10)

  tags = {
    Name = "${local.name}-app-${count.index + 1}"
    Tier = "private-app"
  }
}

resource "aws_subnet" "private_data" {
  count = var.availability_zone_count

  vpc_id            = aws_vpc.main.id
  availability_zone = local.availability_zones[count.index]
  cidr_block        = cidrsubnet(var.vpc_cidr, 8, count.index + 20)

  tags = {
    Name = "${local.name}-data-${count.index + 1}"
    Tier = "private-data"
  }
}

resource "aws_eip" "nat" {
  count = var.availability_zone_count

  domain = "vpc"

  tags = {
    Name = "${local.name}-nat-${count.index + 1}"
  }
}

resource "aws_nat_gateway" "main" {
  count = var.availability_zone_count

  allocation_id = aws_eip.nat[count.index].id
  subnet_id     = aws_subnet.public[count.index].id

  tags = {
    Name = "${local.name}-nat-${count.index + 1}"
  }

  depends_on = [aws_internet_gateway.main]
}

resource "aws_route_table" "public" {
  vpc_id = aws_vpc.main.id

  route {
    cidr_block = "0.0.0.0/0"
    gateway_id = aws_internet_gateway.main.id
  }

  tags = {
    Name = "${local.name}-public"
  }
}

resource "aws_route_table_association" "public" {
  count = var.availability_zone_count

  subnet_id      = aws_subnet.public[count.index].id
  route_table_id = aws_route_table.public.id
}

resource "aws_route_table" "private_app" {
  count = var.availability_zone_count

  vpc_id = aws_vpc.main.id

  route {
    cidr_block     = "0.0.0.0/0"
    nat_gateway_id = aws_nat_gateway.main[count.index].id
  }

  tags = {
    Name = "${local.name}-app-${count.index + 1}"
  }
}

resource "aws_route_table_association" "private_app" {
  count = var.availability_zone_count

  subnet_id      = aws_subnet.private_app[count.index].id
  route_table_id = aws_route_table.private_app[count.index].id
}

resource "aws_route_table" "private_data" {
  count = var.availability_zone_count

  vpc_id = aws_vpc.main.id

  tags = {
    Name = "${local.name}-data-${count.index + 1}"
  }
}

resource "aws_route_table_association" "private_data" {
  count = var.availability_zone_count

  subnet_id      = aws_subnet.private_data[count.index].id
  route_table_id = aws_route_table.private_data[count.index].id
}
