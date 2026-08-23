package com.example.localai.model;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

/** 下载状态机纯逻辑测试。 */
public class DownloadTaskTest {

    private DownloadTask task(DownloadTask.State state) {
        return new DownloadTask("t1", "m1", "M", 1000L, 100L, state);
    }

    @Test
    public void pauseAndResumeRoundTrip() {
        DownloadTask t = task(DownloadTask.State.DOWNLOADING);
        assertTrue(t.transition(DownloadTask.Action.PAUSE));
        assertEquals(DownloadTask.State.PAUSED, t.state);
        assertTrue(t.transition(DownloadTask.Action.RESUME));
        assertEquals(DownloadTask.State.DOWNLOADING, t.state);
    }

    @Test
    public void verifyingThenReady() {
        DownloadTask t = task(DownloadTask.State.DOWNLOADING);
        assertTrue(t.transition(DownloadTask.Action.VERIFY));
        assertEquals(DownloadTask.State.VERIFYING, t.state);
        assertTrue(t.transition(DownloadTask.Action.COMPLETE));
        assertEquals(DownloadTask.State.READY, t.state);
    }

    @Test
    public void failureFromDownloading() {
        DownloadTask t = task(DownloadTask.State.DOWNLOADING);
        assertTrue(t.transition(DownloadTask.Action.FAIL));
        assertEquals(DownloadTask.State.FAILED, t.state);
    }

    @Test
    public void terminalStatesRejectTransitions() {
        assertFalse(task(DownloadTask.State.READY)
                .transition(DownloadTask.Action.PAUSE));
        assertFalse(task(DownloadTask.State.FAILED)
                .transition(DownloadTask.Action.RESUME));
        assertFalse(task(DownloadTask.State.PAUSED)
                .transition(DownloadTask.Action.COMPLETE));
    }

    @Test
    public void percentClampedToZeroAndHundred() {
        DownloadTask t = new DownloadTask("t", "m", "M", 1000L, 2500L,
                DownloadTask.State.DOWNLOADING);
        assertEquals(100, t.percent());
        DownloadTask zero = new DownloadTask("t", "m", "M", 0L, 10L,
                DownloadTask.State.DOWNLOADING);
        assertEquals(0, zero.percent());
    }
}
