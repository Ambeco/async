package com.mpd.concurrent.futures;

import androidx.annotation.CallSuper;

import com.mpd.concurrent.futures.impl.AbstractFuture;

import org.checkerframework.checker.nullness.qual.Nullable;

public class SettableFuture<O> extends AbstractFuture<O> {
	@Override public boolean setResult(O result) {
		return super.setResult(result);
	}

	@Override public void setResult(Future<? extends O> result) {
		super.setResult(result);
	}

	@Override public boolean setException(Throwable exception) {
		return super.setException(exception);
	}

	@CallSuper protected void toStringAppendState(
			boolean isDone,
			@Nullable O result,
			@Nullable Throwable exception,
			@Nullable Future<? extends O> setAsync,
			StringBuilder sb)
	{
		super.toStringAppendState(isDone, result, exception, setAsync, sb);
		if (!isDone && setAsync == null) {
			sb.append(" state=unset");
		}
	}
}
