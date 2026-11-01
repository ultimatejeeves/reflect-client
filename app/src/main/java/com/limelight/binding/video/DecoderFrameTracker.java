package com.limelight.binding.video;

import java.util.LinkedHashMap;

/** Shared input/output bookkeeping. Times use the same monotonic nanosecond clock. */
final class DecoderFrameTracker {
    static final int MAX_PENDING_FRAMES = 256;
    static final long STALL_TIMEOUT_NS = 1_200_000_000L;
    static final long INPUT_ACTIVITY_TIMEOUT_NS = 250_000_000L;

    private final LinkedHashMap<Long, Long> pending = new LinkedHashMap<>();
    private long lastProgressNs;
    private long lastInputActivityNs;
    private long lastRecoveryRequestNs;
    private boolean recoveryRequested;

    synchronized void onInputActivity(long nowNs) {
        lastInputActivityNs = nowNs;
    }

    // Call before queueInputBuffer: output can arrive before that call returns.
    synchronized void onInput(long ptsUs, long nowNs) {
        if (pending.isEmpty()) {
            lastProgressNs = nowNs;
        }
        lastInputActivityNs = nowNs;
        pending.remove(ptsUs);
        pending.put(ptsUs, nowNs);
        if (pending.size() > MAX_PENDING_FRAMES) {
            pending.remove(pending.keySet().iterator().next());
        }
    }

    synchronized void onInputFailed(long ptsUs) {
        pending.remove(ptsUs);
    }

    // Consume on dequeue, even when the caller will discard the output frame.
    synchronized long onOutput(long ptsUs, long nowNs) {
        lastProgressNs = nowNs;
        Long enqueueNs = pending.remove(ptsUs);
        return enqueueNs == null ? -1 : Math.max(0, nowNs - enqueueNs);
    }

    synchronized boolean shouldRequestRecovery(long nowNs) {
        if (pending.isEmpty() || nowNs - lastProgressNs < STALL_TIMEOUT_NS ||
                nowNs - lastInputActivityNs > INPUT_ACTIVITY_TIMEOUT_NS ||
                (recoveryRequested && nowNs - lastRecoveryRequestNs < STALL_TIMEOUT_NS)) {
            return false;
        }
        recoveryRequested = true;
        lastRecoveryRequestNs = nowNs;
        return true;
    }

    synchronized void clear() {
        pending.clear();
        lastProgressNs = 0;
        lastInputActivityNs = 0;
        lastRecoveryRequestNs = 0;
        recoveryRequested = false;
    }

    synchronized int pendingCount() {
        return pending.size();
    }
}
