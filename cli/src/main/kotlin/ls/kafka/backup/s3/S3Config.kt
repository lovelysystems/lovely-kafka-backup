package ls.kafka.backup.s3

data class S3Config(val endpoint: String?, val profile: String? = null, val region: String = "eu-central-1")
