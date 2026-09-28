package com.sahha.file.service.medicalfileservice;

import java.io.FilterInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;

public final class UploadIntegrityInputStream extends FilterInputStream {

	private final MessageDigest digest;
	private final long maximumBytes;
	private long count;

	public UploadIntegrityInputStream(InputStream input, long maximumBytes) {
		super(input);
		if (maximumBytes <= 0) {
			throw new IllegalArgumentException("maximumBytes must be positive");
		}
		this.maximumBytes = maximumBytes;
		try {
			this.digest = MessageDigest.getInstance("SHA-256");
		}
		catch (NoSuchAlgorithmException impossible) {
			throw new IllegalStateException("SHA-256 is unavailable", impossible);
		}
	}

	@Override
	public int read() throws IOException {
		int value = super.read();
		if (value >= 0) {
			record(1);
			digest.update((byte) value);
		}
		return value;
	}

	@Override
	public int read(byte[] bytes, int offset, int length) throws IOException {
		int read = super.read(bytes, offset, length);
		if (read > 0) {
			record(read);
			digest.update(bytes, offset, read);
		}
		return read;
	}

	public long count() {
		return count;
	}

	public String checksumSha256() {
		return HexFormat.of().formatHex(digest.digest());
	}

	private void record(int read) throws IOException {
		count += read;
		if (count > maximumBytes) {
			throw new IOException("upload exceeds its declared size");
		}
	}
}
