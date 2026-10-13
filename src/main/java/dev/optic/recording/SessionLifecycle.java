package dev.optic.recording;

/** Client-thread state and generation guard, independent of Minecraft and native resources. */
public final class SessionLifecycle {
    public enum State { IDLE, PREPARING, RECORDING, STOPPING }
    private State state = State.IDLE;
    private long generation;
    public State state() { return state; }
    public long begin() {
        if (state != State.IDLE) throw new IllegalStateException("A recording operation is already active");
        state = State.PREPARING; return ++generation;
    }
    public boolean current(long id) { return generation == id && state != State.IDLE; }
    public boolean prepared(long id) {
        if (!current(id) || state != State.PREPARING) return false;
        state = State.RECORDING; return true;
    }
    public boolean stopping(long id) {
        if (!current(id)) return false;
        state = State.STOPPING; return true;
    }
    public boolean finished(long id) {
        if (!current(id)) return false;
        state = State.IDLE; return true;
    }
}
