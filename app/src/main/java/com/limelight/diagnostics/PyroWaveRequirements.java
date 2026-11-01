package com.limelight.diagnostics;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

/** Mirrors decoder init's minimum subgroup checks at bitstream reference 186f0393. */
final class PyroWaveRequirements {
    private PyroWaveRequirements() {}

    static JSONObject assess(JSONObject gpu) throws JSONException {
        JSONObject result = new JSONObject();
        if (!"queried".equals(gpu.optString("status")))
            return result.put("status", "unknown_probe_incomplete");
        JSONArray missing = new JSONArray();
        if (!gpu.optBoolean("computeQueue")) missing.put("compute_queue");
        // BASIC, VOTE, ARITHMETIC, BALLOT, SHUFFLE, SHUFFLE_RELATIVE; COMPUTE stage = 0x20.
        if ((gpu.optInt("subgroupOperations") & 0x3f) != 0x3f) missing.put("required_subgroup_operations");
        if ((gpu.optInt("subgroupStages") & 0x20) == 0) missing.put("compute_subgroups");
        if (!gpu.optBoolean("subgroupSizeControl")) missing.put("subgroup_size_control");
        if (!gpu.optBoolean("computeFullSubgroups")) missing.put("compute_full_subgroups");
        int min = gpu.optInt("minSubgroupSize"), max = gpu.optInt("maxSubgroupSize");
        boolean fullRange = min >= 4 && max <= 128;
        boolean overlap = max >= 4 && min <= 128;
        boolean requiredStage = (gpu.optInt("requiredSubgroupSizeStages") & 0x20) != 0;
        if (min <= 0 || max < min || !(fullRange || (overlap && requiredStage)))
            missing.put("compatible_subgroup_size_4_to_128");
        boolean texelFallback = gpu.optLong("maxTexelBufferElements") >= 16L * 1024 * 1024;
        if (!gpu.optBoolean("storageBuffer8BitAccess") && !texelFallback)
            missing.put("8bit_storage_or_16M_texel_buffer_elements");
        result.put("status", missing.length() == 0 ? "candidate_requires_runtime_decode_test" : "missing_required_features");
        result.put("missing", missing);
        // Common::init requires at least 16M texel elements for this fallback.
        result.put("bufferPath", gpu.optBoolean("storageBuffer8BitAccess")
                ? "8bit_storage_advertised" : texelFallback
                ? "texel_buffer_fallback_requires_runtime_test" : "missing_storage_path");
        result.put("surfacePresentation", "not_tested");
        return result;
    }
}
