package com.sahha.file.storage;

import java.io.InputStream;
import java.util.Objects;

import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.CreateBucketRequest;
import software.amazon.awssdk.services.s3.model.DeleteObjectRequest;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.s3.model.HeadBucketRequest;
import software.amazon.awssdk.services.s3.model.HeadObjectRequest;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;
import software.amazon.awssdk.services.s3.model.S3Exception;

/**
 * Private object storage backed by SeaweedFS's S3-compatible gateway.
 *
 * <p>The File Service remains the only caller of the object store. Browser
 * clients never receive SeaweedFS credentials or direct object URLs.</p>
 */
public final class SeaweedFsObjectStorage implements PrivateObjectStorage {

	private final S3Client client;
	private final String bucket;
	private volatile boolean bucketReady;

	public SeaweedFsObjectStorage(S3Client client, String bucket) {
		this.client = Objects.requireNonNull(client);
		this.bucket = required(bucket);
	}

	@Override
	public void put(
			String storageKey,
			InputStream content,
			long size,
			String contentType) {
		if (size < 0) {
			throw new IllegalArgumentException("object size must not be negative");
		}
		try {
			ensureBucket();
			client.putObject(PutObjectRequest.builder()
					.bucket(bucket)
					.key(required(storageKey))
					.contentType(required(contentType))
					.build(), RequestBody.fromInputStream(
							Objects.requireNonNull(content), size));
		}
		catch (Exception exception) {
			throw failure("Unable to store private object.", exception);
		}
	}

	@Override
	public InputStream get(String storageKey) {
		try {
			ensureBucket();
			return client.getObject(GetObjectRequest.builder()
					.bucket(bucket)
					.key(required(storageKey))
					.build());
		}
		catch (Exception exception) {
			throw failure("Unable to read private object.", exception);
		}
	}

	@Override
	public void remove(String storageKey) {
		try {
			ensureBucket();
			client.deleteObject(DeleteObjectRequest.builder()
					.bucket(bucket)
					.key(required(storageKey))
					.build());
		}
		catch (Exception exception) {
			throw failure("Unable to remove private object.", exception);
		}
	}

	@Override
	public boolean exists(String storageKey) {
		try {
			ensureBucket();
			client.headObject(HeadObjectRequest.builder()
					.bucket(bucket)
					.key(required(storageKey))
					.build());
			return true;
		}
		catch (S3Exception exception) {
			if (exception.statusCode() == 404) {
				return false;
			}
			throw failure("Unable to inspect private object.", exception);
		}
		catch (Exception exception) {
			throw failure("Unable to inspect private object.", exception);
		}
	}

	private void ensureBucket() {
		if (bucketReady) {
			return;
		}
		synchronized (this) {
			if (bucketReady) {
				return;
			}
			try {
				client.headBucket(HeadBucketRequest.builder().bucket(bucket).build());
			}
			catch (S3Exception exception) {
				if (exception.statusCode() != 404) {
					throw exception;
				}
				createBucket();
			}
			bucketReady = true;
		}
	}

	private void createBucket() {
		try {
			client.createBucket(CreateBucketRequest.builder().bucket(bucket).build());
		}
		catch (S3Exception exception) {
			// A second File Service instance may create the same private bucket
			// between the head and create operations.
			if (exception.statusCode() != 409) {
				throw exception;
			}
		}
	}

	private static ObjectStorageException failure(
			String message,
			Exception exception) {
		return new ObjectStorageException(message, exception);
	}

	private static String required(String value) {
		Objects.requireNonNull(value);
		String stripped = value.strip();
		if (stripped.isEmpty()) {
			throw new IllegalArgumentException("storage value is required");
		}
		return stripped;
	}
}
