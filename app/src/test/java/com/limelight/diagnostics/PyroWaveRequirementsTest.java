package com.limelight.diagnostics;

import org.json.JSONObject;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;
import static org.junit.Assert.*;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = 33)
public class PyroWaveRequirementsTest {
    private JSONObject gpu() throws Exception {
        return new JSONObject().put("status", "queried").put("computeQueue", true)
                .put("subgroupOperations", 0x3f).put("subgroupStages", 0x20)
                .put("subgroupSizeControl", true).put("computeFullSubgroups", true)
                .put("minSubgroupSize", 32).put("maxSubgroupSize", 64)
                .put("maxTexelBufferElements", 16 * 1024 * 1024);
    }

    @Test public void qualifyingClaimsAreCandidateNotWorkingDecoder() throws Exception {
        JSONObject result = PyroWaveRequirements.assess(gpu());
        assertEquals("candidate_requires_runtime_decode_test", result.getString("status"));
        assertEquals("texel_buffer_fallback_requires_runtime_test", result.getString("bufferPath"));
    }

    @Test public void missingAnyRequiredOperationBlocksCandidate() throws Exception {
        for (int bit = 1; bit <= 0x20; bit <<= 1) {
            assertEquals("missing_required_features", PyroWaveRequirements.assess(
                    gpu().put("subgroupOperations", 0x3f & ~bit)).getString("status"));
        }
    }

    @Test public void partialRangeNeedsExplicitComputeSizeSelection() throws Exception {
        JSONObject device = gpu().put("maxSubgroupSize", 256);
        assertEquals("missing_required_features", PyroWaveRequirements.assess(device).getString("status"));
        device.put("requiredSubgroupSizeStages", 0x20);
        assertEquals("candidate_requires_runtime_decode_test", PyroWaveRequirements.assess(device).getString("status"));
    }

    @Test public void computeFullSubgroupsRequiredEvenWithSizeControl() throws Exception {
        assertEquals("missing_required_features", PyroWaveRequirements.assess(
                gpu().put("computeFullSubgroups", false)).getString("status"));
    }

    @Test public void incompleteProbeIsUnknown() throws Exception {
        assertEquals("unknown_probe_incomplete", PyroWaveRequirements.assess(new JSONObject()).getString("status"));
    }

    @Test public void insufficientTexelCapacityRequires8BitStorage() throws Exception {
        JSONObject device = gpu().put("maxTexelBufferElements", 16 * 1024 * 1024 - 1);
        assertEquals("missing_required_features", PyroWaveRequirements.assess(device).getString("status"));
        device.put("storageBuffer8BitAccess", true);
        assertEquals("candidate_requires_runtime_decode_test", PyroWaveRequirements.assess(device).getString("status"));
    }
}
