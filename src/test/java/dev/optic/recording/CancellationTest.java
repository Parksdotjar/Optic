package dev.optic.recording;

import org.junit.jupiter.api.Test;
import java.util.concurrent.*;
import java.util.concurrent.atomic.*;
import static org.junit.jupiter.api.Assertions.*;

class CancellationTest {
    @Test void cancellationPublishesIntentAndRunsCleanupOffCallerThreadOnce() throws Exception {
        var cancellation=new Cancellation(); var count=new AtomicInteger(); var thread=new AtomicReference<Thread>(); var latch=new CountDownLatch(1);
        try(var registration=cancellation.onCancel(() -> { thread.set(Thread.currentThread()); count.incrementAndGet(); latch.countDown(); })) {
            cancellation.cancel(); cancellation.cancel(); assertThrows(CancellationException.class,cancellation::check);
            assertTrue(latch.await(2,TimeUnit.SECONDS)); assertNotSame(Thread.currentThread(),thread.get()); assertEquals(1,count.get());
        }
    }
    @Test void lateRegistrationStillClosesAndRemovedRegistrationDoesNot() throws Exception {
        var cancellation=new Cancellation(); var removed=new AtomicInteger();
        cancellation.onCancel(removed::incrementAndGet).close(); cancellation.cancel();
        var latch=new CountDownLatch(1); try(var registration=cancellation.onCancel(latch::countDown)) { assertTrue(latch.await(2,TimeUnit.SECONDS)); }
        assertEquals(0,removed.get());
    }
}
