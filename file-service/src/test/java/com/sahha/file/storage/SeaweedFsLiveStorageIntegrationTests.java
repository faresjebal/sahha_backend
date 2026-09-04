package com.sahha.file.storage;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.ByteArrayInputStream;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;

import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.S3Configuration;

@EnabledIfEnvironmentVariable(
		named = "SEAWEEDFS_LIVE_TEST", matches = "(?i)true")
class SeaweedFsLiveStorageIntegrationTests {

	@Test
	void privateAdapterRoundTripsBytesThroughTheLocalS3Gateway() throws Exception {
		try (S3Client client = S3Client.builder()
				.endpointOverride(URI.create("http://127.0.0.1:8333"))
				.region(Region.US_EAST_1)
				.credentialsProvider(StaticCredentialsProvider.create(
						AwsBasicCredentials.create(
								"sahha-local-access-key",
								"sahha-local-secret-key")))
				.serviceConfiguration(S3Configuration.builder()
						.pathStyleAccessEnabled(true)
						.build())
				.build()) {
			PrivateObjectStorage storage = new SeaweedFsObjectStorage(
					client, "sahha-medical-files");
			String key = "live-test/" + UUID.randomUUID();
			byte[] bytes = "synthetic Sahha medical file"
					.getBytes(StandardCharsets.UTF_8);

			try {
				storage.put(key, new ByteArrayInputStream(bytes), bytes.length,
						"application/pdf");
				assertTrue(storage.exists(key));
				try (var stored = storage.get(key)) {
					assertArrayEquals(bytes, stored.readAllBytes());
				}
			}
			finally {
				storage.remove(key);
			}
			assertFalse(storage.exists(key));
		}
	}
}
