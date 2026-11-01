package com.limelight.nvstream.http;

import com.google.gson.JsonObject;
import org.junit.Test;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import static org.junit.Assert.*;

public class HostProtocolCapabilitiesTest {
    @Test public void legacyHostOmitsMask() throws Exception {
        assertEquals(0, HostProtocolCapabilities.parseCodecMask(null));
        assertFalse(new HostProtocolCapabilities(0, new HashMap<>()).supportsUdpCalibrationV1());
    }

    @Test public void unsignedMaskPreservesFutureBitsAcrossJniCast() throws Exception {
        long mask = HostProtocolCapabilities.parseCodecMask(" 4294967295 ");
        assertEquals(0xffffffffL, Integer.toUnsignedLong((int)mask));
    }

    @Test public void malformedMaskBecomesCheckedConnectionError() {
        for (String value : new String[]{"", " ", "-1", "+1", "4294967296", "0x100", "1e3", "1.5", "99999999999999999999"}) {
            try { HostProtocolCapabilities.parseCodecMask(value); fail(value); }
            catch (IOException expected) { /* Connection caller already handles IOException. */ }
        }
    }

    @Test public void ordinaryCodecBitsDoNotImplyPyrowave() {
        JsonObject report = new HostProtocolCapabilities(0x30303, new HashMap<>()).toJson();
        assertFalse(report.get("pyrowaveAdvertised").getAsBoolean());
    }

    @Test public void everyPyrowaveProfileRemainsDisabledUntilClientExists() {
        for (long bit : new long[]{0x00800000, 0x01000000, 0x02000000, 0x04000000}) {
            JsonObject report = new HostProtocolCapabilities(bit, new HashMap<>()).toJson();
            assertTrue(report.get("pyrowaveAdvertised").getAsBoolean());
            assertFalse(report.get("pyrowaveClientImplemented").getAsBoolean());
            assertEquals("not_checked_requires_rtsp_describe", report.get("serverBitstreamStatus").getAsString());
        }
    }

    @Test public void malformedOptionalMetadataDoesNotBreakStandardStreaming() {
        Map<String, String> values = new HashMap<>();
        values.put("PyroWaveHostLinkMbps", "-100");
        values.put("PyroWaveUdpProbeVersion", "garbage");
        JsonObject report = new HostProtocolCapabilities(0x30303, values).toJson();
        assertEquals(2, report.getAsJsonArray("invalidOptionalFields").size());
        assertEquals(0x30303, report.get("serverCodecMask").getAsInt());
        assertFalse(report.get("udpCalibrationV1Advertised").getAsBoolean());
    }

    @Test public void unknownCalibrationVersionIsNotAssumedCompatible() {
        Map<String, String> fields = calibration();
        assertTrue(new HostProtocolCapabilities(0, fields).supportsUdpCalibrationV1());
        fields.put("PyroWaveUdpHandshakeVersion", "2");
        assertFalse(new HostProtocolCapabilities(0, fields).supportsUdpCalibrationV1());
        fields.remove("PyroWaveUdpHandshakeVersion");
        assertFalse(new HostProtocolCapabilities(0, fields).supportsUdpCalibrationV1());
    }

    @Test public void onlyAllowlistedMetadataIsRecordedAndSnapshotCanBeCleared() {
        Map<String, String> fields = calibration();
        fields.put("hostname", "private-host");
        HostProtocolCapabilities caps = new HostProtocolCapabilities(0, fields);
        caps.rememberForDiagnostics();
        assertFalse(HostProtocolCapabilities.lastReport().contains("private-host"));
        caps.toJson().getAsJsonObject("advertisements").addProperty("PyroWaveUdpProbeVersion", 99);
        assertTrue(caps.supportsUdpCalibrationV1());
        HostProtocolCapabilities.clearLastReport();
        assertNull(HostProtocolCapabilities.lastReport());
    }

    private Map<String, String> calibration() {
        Map<String, String> result = new HashMap<>();
        result.put("PyroWaveWireBudgetVersion", "1");
        result.put("PyroWaveUdpProbeVersion", "1");
        result.put("PyroWaveUdpHandshakeVersion", "1");
        result.put("PyroWaveUdpProbeBurstVersion", "1");
        return result;
    }
}
