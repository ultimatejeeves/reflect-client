package com.limelight.nvstream.http;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import java.io.IOException;
import java.util.Map;

/** Optional Vibepollo advertisements; not permission to enable an unimplemented decoder. */
public final class HostProtocolCapabilities {
    public static final long SCM_PYROWAVE_MASK = 0x07800000L;
    public static final String PYROWAVE_BITSTREAM_REFERENCE = "186f0393";
    public static final String[] OPTIONAL_FIELDS = {
            "PyroWaveCompressionVersion", "PyroWaveHostLinkMbps", "PyroWaveBandwidthProbeBytes",
            "PyroWaveWireBudgetVersion", "PyroWaveUdpProbeVersion", "PyroWaveUdpHandshakeVersion",
            "PyroWaveUdpProbeBurstVersion"
    };
    private static volatile String lastReport;
    private final long codecMask;
    private final JsonObject fields = new JsonObject();
    private final JsonArray invalidFields = new JsonArray();

    public HostProtocolCapabilities(long codecMask, Map<String, String> optionalFields) {
        this.codecMask = codecMask;
        for (String name : OPTIONAL_FIELDS) {
            String raw = optionalFields.get(name);
            if (raw == null) continue;
            try {
                fields.addProperty(name, parseCodecMask(raw));
            } catch (IOException e) {
                // Malformed optional extensions must not break H.264/HEVC/AV1 streaming.
                invalidFields.add(name);
            }
        }
    }

    public static long parseCodecMask(String raw) throws IOException {
        if (raw == null) return 0; // Legacy GFE omits the field.
        String value = raw.trim();
        if (!value.matches("[0-9]{1,10}")) throw new IOException("Invalid server codec capability value");
        long mask = Long.parseLong(value);
        if (mask > 0xffffffffL) throw new IOException("Server codec capability value exceeds uint32");
        return mask;
    }

    public boolean supportsUdpCalibrationV1() {
        return isVersionOne("PyroWaveWireBudgetVersion") && isVersionOne("PyroWaveUdpProbeVersion")
                && isVersionOne("PyroWaveUdpHandshakeVersion") && isVersionOne("PyroWaveUdpProbeBurstVersion");
    }

    private boolean isVersionOne(String name) {
        return fields.has(name) && fields.get(name).getAsLong() == 1;
    }

    public JsonObject toJson() {
        JsonObject result = new JsonObject();
        result.addProperty("serverCodecMask", codecMask);
        result.addProperty("pyrowaveAdvertised", (codecMask & SCM_PYROWAVE_MASK) != 0);
        result.addProperty("pyrowave4208Advertised", (codecMask & 0x00800000L) != 0);
        result.addProperty("pyrowave4448Advertised", (codecMask & 0x01000000L) != 0);
        result.addProperty("pyrowave42010Advertised", (codecMask & 0x02000000L) != 0);
        result.addProperty("pyrowave44410Advertised", (codecMask & 0x04000000L) != 0);
        result.addProperty("udpCalibrationV1Advertised", supportsUdpCalibrationV1());
        result.addProperty("pyrowaveClientImplemented", false);
        result.addProperty("pyrowaveBitstreamReference", PYROWAVE_BITSTREAM_REFERENCE);
        result.addProperty("serverBitstreamStatus", "not_checked_requires_rtsp_describe");
        result.add("advertisements", fields.deepCopy());
        result.add("invalidOptionalFields", invalidFields.deepCopy());
        return result;
    }

    public void rememberForDiagnostics() { lastReport = toJson().toString(); }
    /** Process-local, most recent paired launch attempt; no host name, address, cert or pairing ID. */
    public static String lastReport() { return lastReport; }
    public static void clearLastReport() { lastReport = null; }
}
