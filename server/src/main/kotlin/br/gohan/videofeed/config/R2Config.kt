package br.gohan.videofeed.config

import software.amazon.awssdk.auth.credentials.AwsBasicCredentials
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider
import software.amazon.awssdk.regions.Region
import software.amazon.awssdk.services.s3.S3Client
import software.amazon.awssdk.services.s3.presigner.S3Presigner
import java.net.URI

object R2Config {
    val bucket: String = Env.getOrDefault("R2_BUCKET", "videofeed")
    val publicUrl: String = Env.getOrDefault("R2_PUBLIC_URL", "https://pub-placeholder.r2.dev")

    private val accountId = Env.getOrDefault("R2_ACCOUNT_ID", "placeholder")
    private val accessKey = Env.getOrDefault("R2_ACCESS_KEY", "placeholder")
    private val secretKey = Env.getOrDefault("R2_SECRET_KEY", "placeholder")
    private val endpoint = URI.create("https://$accountId.r2.cloudflarestorage.com")

    private val credentials = StaticCredentialsProvider.create(
        AwsBasicCredentials.create(accessKey, secretKey)
    )

    val client: S3Client = S3Client.builder()
        .endpointOverride(endpoint)
        .credentialsProvider(credentials)
        .region(Region.of("auto"))
        .build()

    val presigner: S3Presigner = S3Presigner.builder()
        .endpointOverride(endpoint)
        .credentialsProvider(credentials)
        .region(Region.of("auto"))
        .build()
}
