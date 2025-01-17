package com.tbohne.android.flogger.backend;

import android.util.Log;
import com.google.common.flogger.LogContext;
import com.google.common.flogger.backend.LogData;
import com.google.common.flogger.backend.LoggerBackend;
import com.google.common.flogger.backend.MetadataProcessor;
import com.google.common.flogger.backend.Platform;
import com.google.common.flogger.backend.SimpleMessageFormatter;
import java.util.logging.Level;

public class AndroidBackend extends LoggerBackend {
	private final String tag;

	AndroidBackend(String tag) {
		this.tag = tag;
	}

	@Override public String getLoggerName() {
		return tag;
	}

	@Override public boolean isLoggable(Level lvl) {
		return Log.isLoggable(tag, toAndroidLevel(lvl));
	}

	@Override public void log(LogData data) {
		MetadataProcessor metadata = MetadataProcessor.forScopeAndLogSite(
				Platform.getInjectedMetadata(),
				data.getMetadata());
		String message = SimpleMessageFormatter.getDefaultFormatter().format(data, metadata);
		Throwable thrown = metadata.getSingleValue(LogContext.Key.LOG_CAUSE);
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
		Log.println(toAndroidLevel(badData.getLevel()),
				tag,
				badData + " " + error.getClass() + " " + error.getMessage() + "\n" + Log.getStackTraceString(error));
	}

	private int toAndroidLevel(Level lvl) {
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
}
