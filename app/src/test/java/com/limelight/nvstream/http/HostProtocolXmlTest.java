package com.limelight.nvstream.http;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;
import java.io.IOException;
import static org.junit.Assert.*;
import static org.mockito.Mockito.*;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = 33)
public class HostProtocolXmlTest {
    private final NvHTTP http = mock(NvHTTP.class, CALLS_REAL_METHODS);

    @Test public void legacyXmlRemainsSupported() throws Exception {
        assertEquals(0, http.getServerCodecModeSupport("<root status_code=\"200\"><appversion>7.1.431.0</appversion></root>"));
    }

    @Test public void mixedStandardAndPyrowaveHostPreservesStandardBits() throws Exception {
        long mask = 0x07830303L;
        String xml = "<root status_code=\"200\"><ServerCodecModeSupport>" + mask + "</ServerCodecModeSupport>"
                + "<PyroWaveUdpProbeVersion>1</PyroWaveUdpProbeVersion>"
                + "<PyroWaveHostLinkMbps>invalid</PyroWaveHostLinkMbps></root>";
        assertEquals(mask, http.getServerCodecModeSupport(xml));
        assertEquals(1, http.getHostProtocolCapabilities(xml).toJson().getAsJsonArray("invalidOptionalFields").size());
        assertFalse(http.getHostProtocolCapabilities(xml).toJson().get("pyrowaveClientImplemented").getAsBoolean());
    }

    @Test(expected = IOException.class) public void malformedMaskUsesConnectionIoErrorPath() throws Exception {
        http.getServerCodecModeSupport("<root status_code=\"200\"><ServerCodecModeSupport>not-a-number</ServerCodecModeSupport></root>");
    }
}
