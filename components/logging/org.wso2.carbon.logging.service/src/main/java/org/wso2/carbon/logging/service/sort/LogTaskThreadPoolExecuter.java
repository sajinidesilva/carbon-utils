package org.wso2.carbon.logging.service.sort;

import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

public class LogTaskThreadPoolExecuter {

    private final ArrayBlockingQueue<Runnable> queue = new ArrayBlockingQueue<Runnable>(5);
    ThreadPoolExecutor threadPool = null;
    private int poolSize = 5;
    private int maxPoolSize = 5;
    private long keepAliveTime = 10;

    public LogTaskThreadPoolExecuter() {
        threadPool = new ThreadPoolExecutor(poolSize, maxPoolSize, keepAliveTime,
                TimeUnit.SECONDS, queue);
    }

    public void runTask(Runnable task) {
        threadPool.execute(task);
        System.out.println("Task Count : " + queue.size());
    }
}