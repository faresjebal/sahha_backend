package com.sahha.file.storage;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public final class InMemoryObjectStorage implements PrivateObjectStorage {

	private final Map<String, byte[]> objects = new ConcurrentHashMap<>();

	@Override
	public void put(
			String storageKey,
			InputStream content,
			long size,
			String contentType) {
		try {
			byte[] bytes = content.readAllBytes();
			if (bytes.length != size) {
				throw new IllegalArgumentException("object size does not match declaration");
			}
			objects.put(storageKey, bytes);
		}
		catch (IOException exception) {
			throw new ObjectStorageException("Unable to store private object.", exception);
		}
	}

	@Override
	public InputStream get(String storageKey) {
		byte[] bytes = objects.get(storageKey);
		if (bytes == null) {
			throw new ObjectStorageException(
					"Private object was not found.",
					new IllegalStateException("object-not-found"));
		}
		return new ByteArrayInputStream(bytes.clone());
	}

	@Override
	public void remove(String storageKey) {
		objects.remove(storageKey);
	}

	@Override
	public boolean exists(String storageKey) {
		return objects.containsKey(storageKey);
	}
}
