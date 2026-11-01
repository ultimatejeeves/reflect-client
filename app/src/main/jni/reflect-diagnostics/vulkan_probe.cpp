// Read-only feature enumeration. Does not create a logical device or execute shaders.
#define VK_NO_PROTOTYPES
#include <vulkan/vulkan.h>
#include <jni.h>
#include <dlfcn.h>
#include <algorithm>
#include <cstring>
#include <sstream>
#include <string>
#include <vector>

namespace {
std::string quote(const char *value) {
    std::ostringstream out;
    out << '"';
    for (const unsigned char *p = reinterpret_cast<const unsigned char *>(value); *p; ++p) {
        if (*p == '"' || *p == '\\') out << '\\' << *p;
        else if (*p < 32 || *p >= 127) {
            const char hex[] = "0123456789abcdef";
            out << "\\u00" << hex[*p >> 4] << hex[*p & 15];
        } else out << *p;
    }
    out << '"';
    return out.str();
}

struct Loader {
    void *lib = dlopen("libvulkan.so", RTLD_NOW | RTLD_LOCAL);
    VkInstance instance = VK_NULL_HANDLE;
    PFN_vkDestroyInstance destroy = nullptr;
    ~Loader() {
        if (instance && destroy) destroy(instance, nullptr);
        if (lib) dlclose(lib);
    }
};

std::string collect() {
    Loader loader;
    if (!loader.lib) return "{\"status\":\"vulkan_loader_unavailable\"}";
    auto get = reinterpret_cast<PFN_vkGetInstanceProcAddr>(dlsym(loader.lib, "vkGetInstanceProcAddr"));
    if (!get) return "{\"status\":\"entrypoint_unavailable\"}";
    auto versionFn = reinterpret_cast<PFN_vkEnumerateInstanceVersion>(get(nullptr, "vkEnumerateInstanceVersion"));
    uint32_t version = VK_API_VERSION_1_0;
    if (versionFn && versionFn(&version) != VK_SUCCESS)
        return "{\"status\":\"version_query_failed\"}";
    if (version < VK_API_VERSION_1_1) return "{\"status\":\"vulkan_1_1_unavailable\"}";
    VkApplicationInfo app{};
    app.sType = VK_STRUCTURE_TYPE_APPLICATION_INFO;
    app.pApplicationName = "Reflect capability diagnostics";
    app.apiVersion = std::min(version, VK_API_VERSION_1_3);
    VkInstanceCreateInfo info{};
    info.sType = VK_STRUCTURE_TYPE_INSTANCE_CREATE_INFO;
    info.pApplicationInfo = &app;
    auto create = reinterpret_cast<PFN_vkCreateInstance>(get(nullptr, "vkCreateInstance"));
    if (!create) return "{\"status\":\"create_entrypoint_unavailable\"}";
    VkResult result = create(&info, nullptr, &loader.instance);
    if (result != VK_SUCCESS) return "{\"status\":\"instance_creation_failed\",\"vkResult\":" + std::to_string(result) + "}";
    loader.destroy = reinterpret_cast<PFN_vkDestroyInstance>(get(loader.instance, "vkDestroyInstance"));
    auto enumerate = reinterpret_cast<PFN_vkEnumeratePhysicalDevices>(get(loader.instance, "vkEnumeratePhysicalDevices"));
    auto properties = reinterpret_cast<PFN_vkGetPhysicalDeviceProperties2>(get(loader.instance, "vkGetPhysicalDeviceProperties2"));
    auto features = reinterpret_cast<PFN_vkGetPhysicalDeviceFeatures2>(get(loader.instance, "vkGetPhysicalDeviceFeatures2"));
    auto extensions = reinterpret_cast<PFN_vkEnumerateDeviceExtensionProperties>(get(loader.instance, "vkEnumerateDeviceExtensionProperties"));
    auto queues = reinterpret_cast<PFN_vkGetPhysicalDeviceQueueFamilyProperties>(get(loader.instance, "vkGetPhysicalDeviceQueueFamilyProperties"));
    if (!loader.destroy || !enumerate || !properties || !features || !extensions || !queues)
        return "{\"status\":\"device_query_entrypoint_unavailable\"}";
    uint32_t count = 0;
    if (enumerate(loader.instance, &count, nullptr) != VK_SUCCESS || count > 64)
        return "{\"status\":\"device_enumeration_failed\"}";
    std::vector<VkPhysicalDevice> devices(count);
    if (count && enumerate(loader.instance, &count, devices.data()) != VK_SUCCESS)
        return "{\"status\":\"device_enumeration_incomplete\"}";
    std::ostringstream out;
    out << "{\"status\":\"queried\",\"loaderApiVersion\":" << version << ",\"devices\":[";
    for (uint32_t i = 0; i < count; ++i) {
        if (i) out << ',';
        uint32_t extCount = 0;
        if (extensions(devices[i], nullptr, &extCount, nullptr) != VK_SUCCESS || extCount > 4096) {
            out << "{\"status\":\"extension_query_failed\"}";
            continue;
        }
        std::vector<VkExtensionProperties> ext(extCount);
        if (extCount && extensions(devices[i], nullptr, &extCount, ext.data()) != VK_SUCCESS) {
            out << "{\"status\":\"extension_query_incomplete\"}";
            continue;
        }
        auto has = [&](const char *name) {
            return std::any_of(ext.begin(), ext.begin() + extCount,
                [&](const VkExtensionProperties &e) { return std::strcmp(e.extensionName, name) == 0; });
        };
        VkPhysicalDeviceProperties2 props{};
        props.sType = VK_STRUCTURE_TYPE_PHYSICAL_DEVICE_PROPERTIES_2;
        properties(devices[i], &props);
        uint32_t api = std::min(app.apiVersion, props.properties.apiVersion);
        if (api < VK_API_VERSION_1_1) {
            out << "{\"status\":\"device_vulkan_1_1_unavailable\"}";
            continue;
        }
        const bool controlAvailable = api >= VK_API_VERSION_1_3 || has(VK_EXT_SUBGROUP_SIZE_CONTROL_EXTENSION_NAME);
        const bool storageAvailable = api >= VK_API_VERSION_1_2 || has(VK_KHR_8BIT_STORAGE_EXTENSION_NAME);
        const bool floatAvailable = api >= VK_API_VERSION_1_2 || has(VK_KHR_SHADER_FLOAT16_INT8_EXTENSION_NAME);
        VkPhysicalDeviceSubgroupSizeControlProperties controlProps{};
        controlProps.sType = VK_STRUCTURE_TYPE_PHYSICAL_DEVICE_SUBGROUP_SIZE_CONTROL_PROPERTIES;
        VkPhysicalDeviceSubgroupProperties subgroup{};
        subgroup.sType = VK_STRUCTURE_TYPE_PHYSICAL_DEVICE_SUBGROUP_PROPERTIES;
        subgroup.pNext = controlAvailable ? &controlProps : nullptr;
        props.pNext = &subgroup;
        properties(devices[i], &props);
        VkPhysicalDeviceFeatures2 feats{};
        feats.sType = VK_STRUCTURE_TYPE_PHYSICAL_DEVICE_FEATURES_2;
        VkPhysicalDevice8BitStorageFeatures storage{};
        storage.sType = VK_STRUCTURE_TYPE_PHYSICAL_DEVICE_8BIT_STORAGE_FEATURES;
        VkPhysicalDeviceShaderFloat16Int8Features floats{};
        floats.sType = VK_STRUCTURE_TYPE_PHYSICAL_DEVICE_SHADER_FLOAT16_INT8_FEATURES;
        VkPhysicalDeviceSubgroupSizeControlFeatures control{};
        control.sType = VK_STRUCTURE_TYPE_PHYSICAL_DEVICE_SUBGROUP_SIZE_CONTROL_FEATURES;
        if (storageAvailable) { storage.pNext = feats.pNext; feats.pNext = &storage; }
        if (floatAvailable) { floats.pNext = feats.pNext; feats.pNext = &floats; }
        if (controlAvailable) { control.pNext = feats.pNext; feats.pNext = &control; }
        features(devices[i], &feats);
        uint32_t queueCount = 0;
        queues(devices[i], &queueCount, nullptr);
        if (queueCount > 4096) { out << "{\"status\":\"queue_query_failed\"}"; continue; }
        std::vector<VkQueueFamilyProperties> queueProps(queueCount);
        if (queueCount) queues(devices[i], &queueCount, queueProps.data());
        bool compute = false;
        for (uint32_t q = 0; q < queueCount; ++q)
            compute |= queueProps[q].queueCount && (queueProps[q].queueFlags & VK_QUEUE_COMPUTE_BIT);
        out << "{\"status\":\"queried\",\"name\":" << quote(props.properties.deviceName)
            << ",\"apiVersion\":" << props.properties.apiVersion
            << ",\"driverVersion\":" << props.properties.driverVersion
            << ",\"vendorId\":" << props.properties.vendorID << ",\"deviceId\":" << props.properties.deviceID
            << ",\"computeQueue\":" << (compute ? "true" : "false")
            << ",\"subgroupOperations\":" << subgroup.supportedOperations
            << ",\"subgroupStages\":" << subgroup.supportedStages
            << ",\"subgroupSize\":" << subgroup.subgroupSize
            << ",\"subgroupSizeControl\":" << (control.subgroupSizeControl ? "true" : "false")
            << ",\"computeFullSubgroups\":" << (control.computeFullSubgroups ? "true" : "false")
            << ",\"minSubgroupSize\":" << controlProps.minSubgroupSize
            << ",\"maxSubgroupSize\":" << controlProps.maxSubgroupSize
            << ",\"requiredSubgroupSizeStages\":" << controlProps.requiredSubgroupSizeStages
            << ",\"storageBuffer8BitAccess\":" << (storage.storageBuffer8BitAccess ? "true" : "false")
            << ",\"shaderFloat16\":" << (floats.shaderFloat16 ? "true" : "false")
            << ",\"shaderInt16\":" << (feats.features.shaderInt16 ? "true" : "false")
            << ",\"maxTexelBufferElements\":" << props.properties.limits.maxTexelBufferElements
            << ",\"extensions\":[";
        for (uint32_t e = 0; e < extCount; ++e) { if (e) out << ','; out << quote(ext[e].extensionName); }
        out << "]}";
    }
    out << "]}";
    return out.str();
}
}

extern "C" JNIEXPORT jstring JNICALL
Java_com_limelight_diagnostics_VulkanProbe_nativeCollect(JNIEnv *env, jclass) {
    const std::string report = collect();
    return env->NewStringUTF(report.c_str());
}
