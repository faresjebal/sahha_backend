package com.sahha.file.config;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.S3Configuration;

import com.sahha.file.storage.InMemoryObjectStorage;
import com.sahha.file.storage.PrivateObjectStorage;
import com.sahha.file.storage.SeaweedFsObjectStorage;

@Configuration
@EnableConfigurationProperties(FileStorageProperties.class)
public class FileStorageConfiguration {

	@Bean
	@ConditionalOnProperty(
			name = "sahha.file.storage.provider", havingValue = "seaweedfs")
	S3Client fileSeaweedFsS3Client(FileStorageProperties properties) {
		return S3Client.builder()
				.endpointOverride(properties.endpoint())
				.region(Region.of(properties.region()))
				.credentialsProvider(StaticCredentialsProvider.create(
						AwsBasicCredentials.create(
								properties.accessKey(), properties.secretKey())))
				.serviceConfiguration(S3Configuration.builder()
						.pathStyleAccessEnabled(true)
						.build())
				.build();
	}

	@Bean
	@ConditionalOnProperty(
			name = "sahha.file.storage.provider", havingValue = "seaweedfs")
	PrivateObjectStorage seaweedFsObjectStorage(
			S3Client fileSeaweedFsS3Client,
			FileStorageProperties properties) {
		return new SeaweedFsObjectStorage(
				fileSeaweedFsS3Client, properties.bucket());
	}

	@Bean
	@ConditionalOnProperty(
			name = "sahha.file.storage.provider", havingValue = "memory")
	PrivateObjectStorage inMemoryObjectStorage() {
		return new InMemoryObjectStorage();
	}
}
