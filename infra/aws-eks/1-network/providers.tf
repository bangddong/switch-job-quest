provider "aws" {
  region = var.region

  default_tags {
    tags = {
      Project   = "devquest-eks"
      Layer     = "1-network"
      ManagedBy = "OpenTofu"
    }
  }
}
