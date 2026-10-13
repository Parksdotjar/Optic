package dev.optic.recording;

import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;

/** Cancelling from the client thread only publishes intent; cleanup never runs there. */
public final class Cancellation {
    private static final ExecutorService CLEANUP = Executors.newCachedThreadPool(r -> {
        Thread t = new Thread(r, "Optic cancellation"); t.setDaemon(true); return t;
    });
    private final AtomicBoolean cancelled = new AtomicBoolean();
    private final ConcurrentHashMap<Runnable, Boolean> actions = new ConcurrentHashMap<>();
    public boolean cancelled() { return cancelled.get(); }
    public void check() { if (cancelled()) throw new CancellationException("Recording operation cancelled"); }
    public void cancel() {
        if (cancelled.compareAndSet(false, true)) actions.keySet().forEach(this::dispatch);
    }
    public AutoCloseable onCancel(Runnable action) {
        actions.put(action, true);
        if (cancelled()) dispatch(action);
        return () -> actions.remove(action);
    }
    private void dispatch(Runnable action) {
        if (actions.remove(action) != null) CLEANUP.execute(() -> { try { action.run(); } catch (Exception ignored) {} });
    }
}
