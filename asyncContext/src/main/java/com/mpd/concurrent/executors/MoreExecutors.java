package com.mpd.concurrent.executors;

import com.mpd.concurrent.executors.impl.DirectExecutor;

public class MoreExecutors {
	public static DirectExecutor directExecutor() {
		return DirectExecutor.instance;
	}
}
