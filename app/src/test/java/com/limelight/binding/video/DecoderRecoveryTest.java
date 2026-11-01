package com.limelight.binding.video;

import android.media.MediaCodec;
import com.limelight.preferences.PreferenceConfiguration;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.nio.ByteBuffer;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.atomic.AtomicInteger;
import static org.junit.Assert.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = 33)
public class DecoderRecoveryTest {
    private MediaCodecDecoderRenderer renderer;
    private MediaCodec codec;
    private DecoderFrameTracker tracker;
    private AtomicInteger recovery;
    private VideoStats stats;

    @Before public void setUp() throws Exception {
        // Isolate the real render/recovery methods from device discovery and JNI.
        renderer = mock(MediaCodecDecoderRenderer.class, CALLS_REAL_METHODS);
        codec = mock(MediaCodec.class);
        tracker = new DecoderFrameTracker();
        recovery = new AtomicInteger();
        stats = new VideoStats();
        set("videoDecoder", codec);
        set("decoderFrameTracker", tracker);
        set("codecRecoveryType", recovery);
        set("codecRecoveryMonitor", new Object());
        set("activeWindowVideoStats", stats);
        set("outputBufferQueue", new LinkedBlockingQueue<Integer>());
        set("nextInputBufferIndex", -1);
        PreferenceConfiguration prefs = new PreferenceConfiguration();
        prefs.framePacing = PreferenceConfiguration.FRAME_PACING_MIN_LATENCY;
        set("prefs", prefs);
    }

    @Test public void fastPathCountsDiscardedOutputsAndJoinsRecovery() throws Exception {
        tracker.onInput(100, System.nanoTime());
        tracker.onInput(200, System.nanoTime());
        AtomicInteger outputs = new AtomicInteger();
        when(codec.dequeueOutputBuffer(any(MediaCodec.BufferInfo.class), anyLong())).thenAnswer(call -> {
            int i = outputs.getAndIncrement();
            if (i >= 2) return MediaCodec.INFO_TRY_AGAIN_LATER;
            MediaCodec.BufferInfo info = call.getArgument(0);
            info.presentationTimeUs = (i + 1) * 100;
            return i;
        });
        doAnswer(call -> {
            recovery.set(1); // FLUSH requested after rendering, before the fast-path continue.
            set("codecRecoveryThreadQuiescedFlags", 1); // Input thread already quiesced.
            return null;
        }).when(codec).releaseOutputBuffer(eq(1), anyLong());
        doAnswer(call -> { set("stopping", true); return null; }).when(codec).flush();
        call("startRendererThread");
        Thread thread = (Thread) get("rendererThread");
        try {
            thread.join(3000);
            assertFalse("Fast-path continue must not bypass recovery", thread.isAlive());
            verify(codec).releaseOutputBuffer(0, false);
            verify(codec).releaseOutputBuffer(eq(1), anyLong());
            verify(codec).flush();
            assertEquals(2, get("numFramesOut"));
            assertEquals(2, stats.decoderTimeSamples);
            assertEquals(1, stats.totalFramesRendered);
            assertEquals(0, tracker.pendingCount());
        } finally {
            set("stopping", true);
            thread.interrupt();
            thread.join(1000);
        }
    }

    @Test public void inputBufferWaitParticipatesInRecoveryAndRequestsIdr() throws Exception {
        recovery.set(1);
        set("codecRecoveryThreadQuiescedFlags", 2); // Render thread already quiesced.
        tracker.onInput(100, System.nanoTime());
        assertEquals(false, call("fetchNextInputBuffer"));
        verify(codec).flush();
        verify(codec, never()).dequeueInputBuffer(anyLong());
        assertEquals(0, tracker.pendingCount());
        assertEquals(0, recovery.get());
    }

    @Test public void enqueueTimestampExistsBeforeCodecCanReturnOutput() throws Exception {
        set("nextInputBufferIndex", 0);
        set("nextInputBuffer", ByteBuffer.allocate(16));
        when(codec.dequeueInputBuffer(anyLong())).thenReturn(1);
        when(codec.getInputBuffer(1)).thenReturn(ByteBuffer.allocate(16));
        doAnswer(call -> {
            assertEquals(1, tracker.pendingCount());
            assertTrue(tracker.onOutput(100, System.nanoTime()) >= 0);
            return null;
        }).when(codec).queueInputBuffer(anyInt(), anyInt(), anyInt(), eq(100L), anyInt());
        assertEquals(true, call("queueNextInputBuffer", new Class<?>[]{long.class, int.class}, 100L, 0));
        assertEquals(0, tracker.pendingCount());
    }

    @Test public void codecConfigurationDoesNotCreatePhantomPendingFrame() throws Exception {
        set("nextInputBufferIndex", 0);
        set("nextInputBuffer", ByteBuffer.allocate(16));
        when(codec.dequeueInputBuffer(anyLong())).thenReturn(1);
        when(codec.getInputBuffer(1)).thenReturn(ByteBuffer.allocate(16));
        assertEquals(true, call("queueNextInputBuffer", new Class<?>[]{long.class, int.class},
                0L, MediaCodec.BUFFER_FLAG_CODEC_CONFIG));
        assertEquals(0, tracker.pendingCount());
    }

    @Test public void stoppingNeverRequestsRecoveryOrFlushesCodec() throws Exception {
        long now = System.nanoTime();
        tracker.onInput(100, now - DecoderFrameTracker.STALL_TIMEOUT_NS * 2);
        tracker.onInputActivity(now);
        set("stopping", true);
        call("requestRecoveryIfDecoderStalled");
        assertEquals(0, recovery.get());
        verifyNoInteractions(codec);
    }

    @Test public void failedReleaseIsNotRetriedWithAmbiguousBufferOwnership() throws Exception {
        doThrow(new IllegalStateException("codec failed")).when(codec).releaseOutputBuffer(eq(1), anyLong());
        try {
            call("releaseWithPolicy", new Class<?>[]{int.class, long.class}, 1, System.nanoTime());
            fail("Codec error must reach coordinated recovery");
        } catch (java.lang.reflect.InvocationTargetException e) {
            assertTrue(e.getCause() instanceof IllegalStateException);
        }
        verify(codec, never()).releaseOutputBuffer(anyInt(), anyBoolean());
    }

    private Object call(String name) throws Exception { return call(name, new Class<?>[0]); }
    private Object call(String name, Class<?>[] types, Object... args) throws Exception {
        Method method = MediaCodecDecoderRenderer.class.getDeclaredMethod(name, types);
        method.setAccessible(true);
        return method.invoke(renderer, args);
    }
    private Object get(String name) throws Exception {
        Field field = MediaCodecDecoderRenderer.class.getDeclaredField(name);
        field.setAccessible(true);
        return field.get(renderer);
    }
    private void set(String name, Object value) throws Exception {
        Field field = MediaCodecDecoderRenderer.class.getDeclaredField(name);
        field.setAccessible(true);
        field.set(renderer, value);
    }
}
