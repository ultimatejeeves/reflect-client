package com.limelight.diagnostics;

import androidx.test.core.app.ApplicationProvider;
import com.limelight.nvstream.http.HostProtocolCapabilities;
import org.json.JSONObject;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;
import static org.junit.Assert.*;

@RunWith(RobolectricTestRunner.class)
public class CapabilityReportTest {
    @Test @Config(sdk = {23, 28, 33})
    public void reportSurvivesUnavailableNativeProbeAndOldAndroid() throws Exception {
        HostProtocolCapabilities.clearLastReport();
        JSONObject report = CapabilityReport.collect(ApplicationProvider.getApplicationContext());
        assertEquals(1, report.getInt("schemaVersion"));
        assertEquals("native_probe_unavailable", report.getJSONObject("vulkan").getString("status"));
        assertFalse(report.getJSONObject("pyrowave").getBoolean("clientImplemented"));
        assertTrue(report.isNull("lastPairedHostLaunchAttempt"));
        assertTrue(report.toString(2), report.has("displays"));
        assertFalse(report.getJSONObject("device").has("serial"));
    }
}
