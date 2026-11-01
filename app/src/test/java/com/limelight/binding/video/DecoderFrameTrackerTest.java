package com.limelight.binding.video;

import org.junit.Test;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import static org.junit.Assert.*;

public class DecoderFrameTrackerTest {
    private final DecoderFrameTracker tracker = new DecoderFrameTracker();
    private static final long STALL = DecoderFrameTracker.STALL_TIMEOUT_NS;

    @Test public void outputConsumesTimestampExactlyOnce() {
        tracker.onInput(10, 1_000_000);
        assertEquals(3_000_000, tracker.onOutput(10, 4_000_000));
        assertEquals(-1, tracker.onOutput(10, 5_000_000));
        assertEquals(0, tracker.pendingCount());
    }

    @Test public void reorderedOutputsKeepTheirOwnTimestamps() {
        tracker.onInput(10, 100);
        tracker.onInput(20, 200);
        assertEquals(100, tracker.onOutput(20, 300));
        assertEquals(300, tracker.onOutput(10, 400));
    }

    @Test public void missingOutputsCannotGrowBookkeepingWithoutBound() {
        for (int i = 0; i < 10000; i++) tracker.onInput(i, i);
        assertEquals(DecoderFrameTracker.MAX_PENDING_FRAMES, tracker.pendingCount());
        assertEquals(-1, tracker.onOutput(0, 10000));
        assertEquals(1, tracker.onOutput(9999, 10000));
    }

    @Test public void failedInputIsNotCountedOrConsideredPending() {
        tracker.onInput(10, 0);
        tracker.onInputFailed(10);
        tracker.onInputActivity(STALL);
        assertFalse(tracker.shouldRequestRecovery(STALL));
        assertEquals(-1, tracker.onOutput(10, STALL));
    }

    @Test public void quietStreamDoesNotTriggerRecovery() {
        tracker.onInput(10, 0);
        assertFalse(tracker.shouldRequestRecovery(STALL * 10));
        tracker.clear();
        tracker.onInputActivity(STALL * 10);
        assertFalse(tracker.shouldRequestRecovery(STALL * 10));
    }

    @Test public void activeInputWithoutOutputRequestsRecoveryAtDeadline() {
        tracker.onInput(10, 0);
        tracker.onInputActivity(STALL - 1);
        assertFalse(tracker.shouldRequestRecovery(STALL - 1));
        assertTrue(tracker.shouldRequestRecovery(STALL));
        assertFalse(tracker.shouldRequestRecovery(STALL + 1));
        tracker.onInputActivity(STALL * 2);
        assertTrue(tracker.shouldRequestRecovery(STALL * 2));
    }

    @Test public void outputProgressResetsStallDeadlineEvenWithUnknownPts() {
        tracker.onInput(10, 0);
        tracker.onOutput(99, STALL - 1);
        tracker.onInputActivity(STALL);
        assertFalse(tracker.shouldRequestRecovery(STALL));
    }

    @Test public void recoveryClearsOldTimestampsAndGivesNewInputFullGracePeriod() {
        tracker.onInput(10, 0);
        tracker.clear();
        assertEquals(-1, tracker.onOutput(10, STALL));
        tracker.onInput(20, STALL * 10);
        assertFalse(tracker.shouldRequestRecovery(STALL * 10));
        tracker.onInputActivity(STALL * 11);
        assertTrue(tracker.shouldRequestRecovery(STALL * 11));
    }

    @Test public void duplicatePtsReplacesOldRecordWithoutGrowingMap() {
        tracker.onInput(10, 100);
        tracker.onInput(10, 200);
        assertEquals(1, tracker.pendingCount());
        assertEquals(100, tracker.onOutput(10, 300));
    }

    @Test public void producerAndConsumerCanRunConcurrently() throws Exception {
        ExecutorService workers = Executors.newFixedThreadPool(2);
        CountDownLatch start = new CountDownLatch(1);
        try {
            Future<?> producer = workers.submit(() -> {
                await(start);
                for (int i = 0; i < 10000; i++) tracker.onInput(i, i);
            });
            Future<?> consumer = workers.submit(() -> {
                await(start);
                for (int i = 0; i < 10000; i++) tracker.onOutput(i, 10000 + i);
            });
            start.countDown();
            producer.get(5, TimeUnit.SECONDS);
            consumer.get(5, TimeUnit.SECONDS);
            assertTrue(tracker.pendingCount() <= DecoderFrameTracker.MAX_PENDING_FRAMES);
            tracker.clear();
            assertEquals(0, tracker.pendingCount());
        } finally {
            workers.shutdownNow();
        }
    }

    private static void await(CountDownLatch latch) {
        try { latch.await(); }
        catch (InterruptedException e) { Thread.currentThread().interrupt(); throw new AssertionError(e); }
    }
}
