package com.limelight.diagnostics;

/** No Vulkan linkage at app startup: older devices can still use MediaCodec. */
public final class VulkanProbe {
    private VulkanProbe() {}

    public static String collect() {
        try {
            System.loadLibrary("reflect-diagnostics");
            return nativeCollect();
        } catch (LinkageError e) {
            return "{\"status\":\"native_probe_unavailable\"}";
        }
    }

    private static native String nativeCollect();
}
