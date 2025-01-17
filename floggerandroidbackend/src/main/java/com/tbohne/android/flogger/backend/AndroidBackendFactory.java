package com.tbohne.android.flogger.backend;

import com.google.common.flogger.backend.LoggerBackend;
import com.google.common.flogger.backend.system.BackendFactory;
import java.util.concurrent.atomic.AtomicReferenceArray;

public class AndroidBackendFactory extends BackendFactory {
	public static final AndroidBackendFactory INSTANCE = new AndroidBackendFactory();
	private static final int CACHE_SIZE = 64;
	private static final int MAX_TAG_LEN = 23;
	private final AtomicReferenceArray<LoggerBackend> caches = new AtomicReferenceArray<>(CACHE_SIZE);

	public static final AndroidBackendFactory getInstance() {
		return INSTANCE;
	}

	private static String tagFromClassName(String loggingClassName) {
		int lastDot = loggingClassName.lastIndexOf('.') + 1;
		if (lastDot == 0 && loggingClassName.length() <= MAX_TAG_LEN) {
			return loggingClassName;
		} else if (lastDot + MAX_TAG_LEN <= loggingClassName.length()) {
			return loggingClassName.substring(lastDot, lastDot + MAX_TAG_LEN);
		} else {
			return loggingClassName.substring(lastDot);
		}
	}

	@Override public LoggerBackend create(String loggingClassName) {
		String tag = tagFromClassName(loggingClassName);
		int hashCode = tag.hashCode() % CACHE_SIZE;
		LoggerBackend cached = caches.get(hashCode);
		if (cached != null && cached.getLoggerName().equals(tag)) {
			return cached;
		}
		LoggerBackend newBackend = new AndroidBackend(tag);
		caches.set(hashCode, newBackend);
		return newBackend;
	}
}
