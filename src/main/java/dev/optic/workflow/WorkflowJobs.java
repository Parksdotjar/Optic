package dev.optic.workflow;
import java.util.concurrent.*;

/** One disk worker and eight pending UI operations, independent of capture workers. */
public final class WorkflowJobs {
    private static final ThreadPoolExecutor WORK=new ThreadPoolExecutor(1,1,0,TimeUnit.SECONDS,new ArrayBlockingQueue<>(8),r -> { var t=new Thread(r,"Optic library"); t.setDaemon(true); return t; },new ThreadPoolExecutor.AbortPolicy());
    public static boolean execute(Runnable job) {
        try { WORK.execute(job); return true; }
        catch(RejectedExecutionException e) { dev.optic.OpticClient.notice("Library is busy. Wait for the current operation, then retry."); return false; }
    }
}
