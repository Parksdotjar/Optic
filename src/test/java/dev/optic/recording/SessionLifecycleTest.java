package dev.optic.recording;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class SessionLifecycleTest {
    @Test void cancelledPreparationCannotBecomeRecordingAndOldCallbacksCannotFinishNewSession() {
        var lifecycle=new SessionLifecycle(); long cancelled=lifecycle.begin();
        lifecycle.stopping(cancelled); assertFalse(lifecycle.prepared(cancelled));
        assertTrue(lifecycle.finished(cancelled)); long active=lifecycle.begin();
        assertFalse(lifecycle.finished(cancelled)); assertFalse(lifecycle.prepared(cancelled));
        assertEquals(SessionLifecycle.State.PREPARING,lifecycle.state());
        assertTrue(lifecycle.prepared(active)); assertEquals(SessionLifecycle.State.RECORDING,lifecycle.state());
    }
    @Test void hundredRapidCyclesIncludeCancelDuplicateStartRepeatedStopAndLateCallback() {
        var lifecycle=new SessionLifecycle();
        for(int i=0;i<100;i++) {
            long id=lifecycle.begin(); assertThrows(IllegalStateException.class,lifecycle::begin);
            if(i%2==0) assertTrue(lifecycle.prepared(id));
            assertTrue(lifecycle.stopping(id)); assertTrue(lifecycle.stopping(id));
            assertFalse(lifecycle.prepared(id)); assertTrue(lifecycle.finished(id)); assertFalse(lifecycle.finished(id));
            assertEquals(SessionLifecycle.State.IDLE,lifecycle.state());
        }
    }
}
