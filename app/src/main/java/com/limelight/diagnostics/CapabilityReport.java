package com.limelight.diagnostics;

import android.content.Context;
import android.hardware.display.DisplayManager;
import android.media.MediaCodecInfo;
import android.media.MediaCodecList;
import android.os.Build;
import android.view.Display;
import com.limelight.BuildConfig;
import com.limelight.nvstream.http.HostProtocolCapabilities;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

/** Device claims only. Export explicitly; never run on the streaming or UI thread. */
public final class CapabilityReport {
    private CapabilityReport() {}

    public static JSONObject collect(Context context) throws JSONException {
        JSONObject report = new JSONObject();
        report.put("schemaVersion", 1);
        report.put("collectedAtEpochMs", System.currentTimeMillis());
        report.put("app", new JSONObject().put("package", context.getPackageName())
                .put("version", BuildConfig.VERSION_NAME).put("versionCode", BuildConfig.VERSION_CODE)
                .put("buildType", BuildConfig.BUILD_TYPE));
        report.put("device", new JSONObject().put("manufacturer", Build.MANUFACTURER)
                .put("model", Build.MODEL).put("sdk", Build.VERSION.SDK_INT)
                .put("androidRelease", Build.VERSION.RELEASE).put("buildId", Build.ID)
                .put("securityPatch", Build.VERSION.SDK_INT >= 23 ? Build.VERSION.SECURITY_PATCH : JSONObject.NULL)
                .put("abis", new JSONArray(Build.SUPPORTED_ABIS)));
        report.put("scope", "Driver/API claims, not measured decode speed, HDR correctness or display latency. No serial, network address or account data collected.");
        try { report.put("displays", displays(context)); }
        catch (RuntimeException e) { report.put("displayError", e.getClass().getSimpleName()); }
        try { report.put("mediaCodecDecoders", decoders()); }
        catch (RuntimeException e) { report.put("codecError", e.getClass().getSimpleName()); }
        try {
            JSONObject vulkan = new JSONObject(VulkanProbe.collect());
            JSONArray devices = vulkan.optJSONArray("devices");
            if (devices != null) for (int i = 0; i < devices.length(); i++) {
                JSONObject gpu = devices.getJSONObject(i);
                gpu.put("pyrowaveMinimumFeatures", PyroWaveRequirements.assess(gpu));
            }
            report.put("vulkan", vulkan);
        }
        catch (RuntimeException | JSONException e) { report.put("vulkanError", e.getClass().getSimpleName()); }
        String host = HostProtocolCapabilities.lastReport();
        report.put("lastPairedHostLaunchAttempt", host == null ? JSONObject.NULL : new JSONObject(host));
        report.put("pyrowave", new JSONObject().put("clientImplemented", false)
                .put("referenceBitstream", HostProtocolCapabilities.PYROWAVE_BITSTREAM_REFERENCE)
                .put("status", "requires_native_decoder_transport_and_presentation_port")
                .put("runtimeDecodeTest", "not_run").put("networkCalibration", "not_implemented"));
        return report;
    }

    private static JSONArray displays(Context context) throws JSONException {
        JSONArray results = new JSONArray();
        DisplayManager manager = (DisplayManager) context.getSystemService(Context.DISPLAY_SERVICE);
        for (Display display : manager.getDisplays()) {
            JSONObject item = new JSONObject().put("id", display.getDisplayId())
                    .put("refreshRate", display.getRefreshRate());
            if (Build.VERSION.SDK_INT >= 23) {
                item.put("activeModeId", display.getMode().getModeId());
                JSONArray modes = new JSONArray();
                for (Display.Mode mode : display.getSupportedModes()) {
                    modes.put(new JSONObject().put("id", mode.getModeId()).put("width", mode.getPhysicalWidth())
                            .put("height", mode.getPhysicalHeight()).put("refreshRate", mode.getRefreshRate()));
                }
                item.put("modes", modes);
            }
            if (Build.VERSION.SDK_INT >= 24 && display.getHdrCapabilities() != null) {
                // Types: 1 Dolby Vision, 2 HDR10, 3 HLG, 4 HDR10+. No luminance claims.
                item.put("hdrTypes", new JSONArray(display.getHdrCapabilities().getSupportedHdrTypes()));
            }
            results.put(item);
        }
        return results;
    }

    private static JSONArray decoders() throws JSONException {
        JSONArray results = new JSONArray();
        for (MediaCodecInfo codec : new MediaCodecList(MediaCodecList.ALL_CODECS).getCodecInfos()) {
            if (codec.isEncoder()) continue;
            for (String mime : codec.getSupportedTypes()) {
                if (!mime.equalsIgnoreCase("video/avc") && !mime.equalsIgnoreCase("video/hevc")
                        && !mime.equalsIgnoreCase("video/av01")) continue;
                JSONObject item = new JSONObject().put("name", codec.getName()).put("mime", mime);
                results.put(item);
                try {
                    if (Build.VERSION.SDK_INT >= 29) {
                        item.put("hardwareAccelerated", codec.isHardwareAccelerated())
                                .put("softwareOnly", codec.isSoftwareOnly()).put("vendor", codec.isVendor())
                                .put("alias", codec.isAlias());
                    }
                    MediaCodecInfo.CodecCapabilities caps = codec.getCapabilitiesForType(mime);
                    item.put("adaptivePlayback", caps.isFeatureSupported(MediaCodecInfo.CodecCapabilities.FEATURE_AdaptivePlayback));
                    item.put("securePlaybackRequired", caps.isFeatureRequired(MediaCodecInfo.CodecCapabilities.FEATURE_SecurePlayback));
                    item.put("tunneledPlaybackRequired", caps.isFeatureRequired(MediaCodecInfo.CodecCapabilities.FEATURE_TunneledPlayback));
                    if (Build.VERSION.SDK_INT >= 30)
                        item.put("lowLatency", caps.isFeatureSupported(MediaCodecInfo.CodecCapabilities.FEATURE_LowLatency));
                    JSONArray profiles = new JSONArray();
                    for (MediaCodecInfo.CodecProfileLevel profile : caps.profileLevels)
                        profiles.put(new JSONObject().put("profile", profile.profile).put("level", profile.level));
                    item.put("profileLevels", profiles);
                    MediaCodecInfo.VideoCapabilities video = caps.getVideoCapabilities();
                    if (video != null) {
                        item.put("widthAlignment", video.getWidthAlignment()).put("heightAlignment", video.getHeightAlignment());
                        JSONArray checks = new JSONArray();
                        int[][] sizes = {{1920, 1080}, {2560, 1440}, {3840, 2160}, {3088, 1440}, {2800, 1752}};
                        for (int[] size : sizes) for (int fps : new int[]{60, 120}) {
                            checks.put(new JSONObject().put("width", size[0]).put("height", size[1]).put("fps", fps)
                                    .put("reportedSupported", video.areSizeAndRateSupported(size[0], size[1], fps)));
                        }
                        item.put("sizeRateClaims", checks);
                    }
                } catch (RuntimeException e) { item.put("error", e.getClass().getSimpleName()); }
            }
        }
        return results;
    }
}
