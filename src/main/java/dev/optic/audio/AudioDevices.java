package dev.optic.audio;

import java.nio.file.Path;
import java.io.IOException;
import java.util.concurrent.*;
import java.util.function.Supplier;

/** Native audio drivers can block. Bound their caller's wait and the number of driver workers. */
public final class AudioDevices {
    private static ThreadPoolExecutor pool(String name) {
        return new ThreadPoolExecutor(1,1,0,TimeUnit.SECONDS,new ArrayBlockingQueue<>(2),r -> {
            Thread t=new Thread(r,name); t.setDaemon(true); return t;
        },new ThreadPoolExecutor.AbortPolicy());
    }
    private static final ThreadPoolExecutor OPEN=pool("Optic audio setup"), CLOSE=pool("Optic audio cleanup");
    public static AudioCapture start(Supplier<AudioCapture> factory,Path path) throws Exception {
        if(CLOSE.getQueue().remainingCapacity()==0) throw new IOException("Microphone driver is busy cleaning up a previous device");
        CompletableFuture<AudioCapture> result=new CompletableFuture<>();
        OPEN.execute(() -> {
            AudioCapture audio=null;
            try { audio=factory.get(); audio.start(path); result.complete(audio); }
            catch(Exception e) { result.completeExceptionally(e); if(audio!=null) closeLater(audio); }
        });
        try { return result.get(3,TimeUnit.SECONDS); }
        catch(TimeoutException | InterruptedException e) {
            // Late successful setup belongs to nobody and must immediately be stopped.
            result.thenAccept(AudioDevices::closeLater);
            if(e instanceof InterruptedException) Thread.currentThread().interrupt();
            throw new IOException("Microphone setup timed out; video continues",e);
        } catch(ExecutionException e) { throw new IOException("Microphone unavailable",e.getCause()); }
    }
    private static void closeLater(AudioCapture audio) {
        audio.requestStop();
        try { CLOSE.execute(() -> { try { audio.close(); } catch(Exception ignored) {} }); }
        catch(RejectedExecutionException e) { org.slf4j.LoggerFactory.getLogger("Optic").warn("Audio driver cleanup queue is stalled",e); }
    }
    public static void close(AudioCapture audio) throws Exception {
        audio.requestStop();
        Future<?> task=CLOSE.submit(() -> { try { audio.close(); } catch(Exception e) { throw new CompletionException(e); } });
        try { task.get(2,TimeUnit.SECONDS); }
        catch(TimeoutException e) { throw new IOException("Microphone driver cleanup is delayed; video will continue finalizing",e); }
    }
}
