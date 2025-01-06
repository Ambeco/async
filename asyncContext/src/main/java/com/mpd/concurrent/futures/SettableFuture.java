package com.mpd.concurrent.futures;

import androidx.annotation.CallSuper;

import com.mpd.concurrent.futures.atomic.AbstractFuture;

import org.checkerframework.checker.nullness.qual.Nullable;

public class SettableFuture<O> extends AbstractFuture<O> {
	@Override public boolean setResult(O result) {
		return super.setResult(result);
	}

	@Override public boolean setResult(Future<? extends O> result) {
		return super.setResult(result);
	}

	@Override public boolean setException(Throwable exception) {
		return super.setException(exception);
	}

	@CallSuper protected void toStringAppendState(
			@Nullable O result, @Nullable Throwable exception, @Nullable Future<? extends O> setAsync, StringBuilder sb)
	{
		super.toStringAppendState(result, exception, setAsync, sb);
		if (exception == null && setAsync == null) {
			sb.append(" state=unset");
		}
	}
}
