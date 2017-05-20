package com.github.syncservice;

import java.util.concurrent.Future;
import java.util.concurrent.FutureTask;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Callable;
import java.lang.InterruptedException;
import java.util.concurrent.TimeUnit;

public class TaskExecutor {
    class BGTask extends FutureTask<Void> {
        public BGTask(ATask a, Callable completion) {
            super(a, null);
            mTask = a;
            mCompletion = completion;
        }

        @Override
        protected void done() {
            try {
                if (mCompletion != null) {
                    mCompletion.call();
                }
            } catch (Exception e) {
            }
        }

        private ATask mTask;
        private Callable mCompletion;
    }

    public TaskExecutor() {
        mPool = Executors.newFixedThreadPool(3);
    }

    public Future<Void> execute(ATask task, Callable completion) {
        BGTask bg = new BGTask(task, completion);
        mPool.execute(bg);
        return bg;
    }

    public void shutdown() {
        mPool.shutdownNow();
        try {
            mPool.awaitTermination(60, TimeUnit.SECONDS);
        } catch (InterruptedException e) {
        }
    }

    private ExecutorService mPool;
}
