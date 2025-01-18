package com.tbohne.android.flogger.backend;

import androidx.annotation.Keep;
import com.google.common.flogger.backend.system.BackendFactory;
import java.util.concurrent.atomic.AtomicReferenceArray;
import org.checkerframework.checker.nullness.qual.Nullable;

@Keep public class AndroidBackendFactory extends BackendFactory {
	private static final int CACHE_SIZE = 64;
	private static final int MAX_TAG_LEN = 23;
	public static final AndroidBackendFactory INSTANCE = new AndroidBackendFactory();
	private static final String[] PACKAGE_IGNORE_LIST = new String[]{
			"test", "impl", "interface", "public", "common", "base", "util"};
	private static volatile String fallbackTag = null;
	private final AtomicReferenceArray<AndroidBackend> caches = new AtomicReferenceArray<>(CACHE_SIZE);

	@Keep public static AndroidBackendFactory getInstance() {
		return INSTANCE;
	}

	/*
	 * Class names are too granular for Android log tags, so this extracts the innermost package name
	 *
	 * If proguard removed the packages, then use retry with BuildConfig, and let the backend use a stock tag.
	 * If the innermost package name is in PACKAGE_IGNORE_LIST, then use the next innermost package instead.
	 */
	private static @Nullable String tagFromClassName(@Nullable String loggingClassName) {
		if (loggingClassName == null) {
			return getFallbackTag();
		}
		int lastDot = loggingClassName.lastIndexOf('.');
		if (lastDot <= 0) { // If proguard removed the packages, then use "", and let the backend use a stock tag.
			return getFallbackTag();
		}
		int secondLastDot = loggingClassName.lastIndexOf('.', lastDot - 1); // use the innermost package name as the tag
		if (lastDot - secondLastDot > MAX_TAG_LEN) { // if package name is too long, truncate
			lastDot = secondLastDot + MAX_TAG_LEN;
		}
		String tag = loggingClassName.substring(secondLastDot + 1, lastDot);
		boolean ignored = false;  // check if the package is in the PACKAGE_IGNORE_LIST
		for (int i = 0; i < PACKAGE_IGNORE_LIST.length; i++) {
			if (PACKAGE_IGNORE_LIST[i].equals(tag)) {
				ignored = true;
				break;
			}
		}
		if (!ignored) { // if not: done
			return tag;
		} else if (secondLastDot <= 0) {
			return getFallbackTag();
		}
		// otherwise, use next innermost package instead.
		lastDot = secondLastDot;
		secondLastDot = loggingClassName.lastIndexOf('.', lastDot - 1);
		if (lastDot - secondLastDot > MAX_TAG_LEN) { // if package name is too long, truncate
			lastDot = secondLastDot + MAX_TAG_LEN;
		}
		return loggingClassName.substring(secondLastDot + 1, lastDot);
	}

	private static String getFallbackTag() {
		if (fallbackTag != null) {
			return fallbackTag;
		}
		fallbackTag = "NoTag";
		return fallbackTag;
	}

	@Override public AndroidBackend create(@Nullable String loggingClassName) {
		String tag = tagFromClassName(loggingClassName);
		int hashCode = tag != null ? Math.abs(tag.hashCode() % CACHE_SIZE) : 0;
		AndroidBackend cached = caches.get(hashCode);
		if (cached != null && cached.getLoggerName().equals(tag)) {
			return cached;
		}
		AndroidBackend newBackend = new AndroidBackend(tag);
		caches.set(hashCode, newBackend);
		return newBackend;
	}
}
