package org.codeontology.util;

import java.util.concurrent.*;

public class ThreadPoolUtil {

    private ExecutorService threadPool = newThreadPool();
    private static ThreadPoolUtil instance;

    public static ThreadPoolUtil getInstance(){
        if (instance == null) {
            instance = new ThreadPoolUtil();
        }
        return instance;
    }

    public static ExecutorService newThreadPool(){
        return new ThreadPoolExecutor(40, 40, 0L, TimeUnit.MILLISECONDS,
                new ArrayBlockingQueue<Runnable>(5),//线程等待队列长度
                new ThreadPoolExecutor.CallerRunsPolicy()//任务拒绝策略：线程池拒绝该任务的时，线程在本地线程直接execute
        );
//        return Executors.newFixedThreadPool(10);
    }

    public ExecutorService getThreadPool(){
        return threadPool;
    }

    public void execute(Runnable runnable){
        threadPool.execute(runnable);
    }

    public void awaitTermination(){
        threadPool.shutdown();
        try {
            while(!threadPool.awaitTermination(2, TimeUnit.SECONDS));//等待线程池执行完，每2s检查一次
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
        threadPool = newThreadPool();
    }

    public static void clean(){
        if(instance!= null && instance.threadPool!=null){
            instance.threadPool.shutdown();
            instance.threadPool = null;
        }
        instance = null;
    }

}
