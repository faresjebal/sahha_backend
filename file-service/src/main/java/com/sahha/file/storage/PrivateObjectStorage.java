package com.sahha.file.storage;

import java.io.InputStream;

public interface PrivateObjectStorage {

	void put(String storageKey, InputStream content, long size, String contentType);

	InputStream get(String storageKey);

	void remove(String storageKey);

	boolean exists(String storageKey);
}
