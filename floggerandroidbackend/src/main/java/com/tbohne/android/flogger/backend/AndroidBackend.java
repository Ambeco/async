package com.tbohne.android.flogger.backend;

import android.util.Log;
import com.google.common.flogger.LogContext.Key;
import com.google.common.flogger.backend.LogData;
import com.google.common.flogger.backend.LoggerBackend;
import com.google.common.flogger.backend.MetadataProcessor;
import com.google.common.flogger.backend.Platform;
import com.google.common.flogger.backend.SimpleMessageFormatter;
import java.util.logging.Level;
import org.checkerframework.checker.nullness.qual.Nullable;

public class AndroidBackend extends LoggerBackend {
	private static int androidLogLevelOverride = -1;

	private final String tag;

	AndroidBackend(@Nullable String tag) {
		this.tag = tag;
	}

	public static void setLogLevelOverride(int androidLogLevel) {
		androidLogLevelOverride = androidLogLevel;
	}

	@Override public String getLoggerName() {
		return tag;
	}

	private static int toAndroidLevel(Level lvl) {
		if (lvl.intValue() <= Level.FINER.intValue()) {
			return Log.VERBOSE;
		} else if (lvl.intValue() <= Level.FINE.intValue()) {
			return Log.DEBUG;
		} else if (lvl.intValue() <= Level.INFO.intValue()) {
			return Log.INFO;
		} else if (lvl.intValue() <= Level.WARNING.intValue()) {
			return Log.WARN;
		} else if (lvl.intValue() <= Level.SEVERE.intValue()) {
			return Log.ERROR;
		} else {
			return Log.ASSERT;
		}
	}

	@Override public boolean isLoggable(Level lvl) {
		int androidLogLevel = toAndroidLevel(lvl);
		if (androidLogLevelOverride != -1) {
			return androidLogLevel >= androidLogLevelOverride;
		}
		return Log.isLoggable(tag, androidLogLevel);
	}

	@Override public void log(LogData data) {
		MetadataProcessor metadata = MetadataProcessor.forScopeAndLogSite(
				Platform.getInjectedMetadata(),
				data.getMetadata());
		String message = SimpleMessageFormatter.getDefaultFormatter().format(data, metadata);
		Throwable thrown = metadata.getSingleValue(Key.LOG_CAUSE);
		if (thrown == null) {
			Log.println(toAndroidLevel(data.getLevel()), tag, message);
		} else {
			switch (toAndroidLevel(data.getLevel())) {
				case Log.VERBOSE:
					Log.v(tag, message, thrown);
					break;
				case Log.DEBUG:
					Log.d(tag, message, thrown);
					break;
				case Log.INFO:
					Log.i(tag, message, thrown);
					break;
				case Log.WARN:
					Log.w(tag, message, thrown);
					break;
				case Log.ERROR:
					Log.e(tag, message, thrown);
					break;
				default:
					Log.wtf(tag, message, thrown);
					break;
			}
		}
	}

	@Override public void handleError(RuntimeException error, LogData badData) {
		MetadataProcessor metadata = MetadataProcessor.forScopeAndLogSite(Platform.getInjectedMetadata(),
				badData.getMetadata());
		String message = SimpleMessageFormatter.getDefaultFormatter().format(badData, metadata);
		Throwable thrown = metadata.getSingleValue(Key.LOG_CAUSE);
		Log.e(tag, message, thrown);
	}
}
