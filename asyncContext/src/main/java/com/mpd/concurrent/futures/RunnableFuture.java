package com.mpd.concurrent.futures;

// #run will complete the future, one way or another
public interface RunnableFuture<O> extends SubmittableFuture<O> {}
