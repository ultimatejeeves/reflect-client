// Reflect diagnostic harness. Uses the pinned PyroWave C API (MIT, Hans-Kristian Arntzen).
// The fixture container below is test-only and is NOT GameStream record framing.
#define VK_NO_PROTOTYPES
#include <vulkan/vulkan.h>
#include "pyrowave.h"
#include <algorithm>
#include <cstdio>
#include <cstdlib>
#include <cstring>
#include <fstream>
#include <memory>
#include <stdexcept>
#include <string>
#include <vector>
#include <thread>
#include <chrono>
#ifdef _WIN32
#define WIN32_LEAN_AND_MEAN
#include <windows.h>
#else
#include <dlfcn.h>
#endif

static void check(pyrowave_result result, const char *stage) {
    std::printf("%s: %d\n", stage, int(result));
    std::fflush(stdout);
    if (result != PYROWAVE_SUCCESS) throw std::runtime_error(stage);
}
static void require(bool condition, const char *message) {
    if (!condition) throw std::runtime_error(message);
}
struct Device {
    pyrowave_device value = nullptr;
    Device() {
        check(pyrowave_create_default_device(&value), "device_create");
        VkInstance instance = VK_NULL_HANDLE;
        VkPhysicalDevice physical = VK_NULL_HANDLE;
        pyrowave_device_get_vk_device_handles(value, &instance, &physical, nullptr);
#ifdef _WIN32
        auto loader = LoadLibraryA("vulkan-1.dll");
        auto get = loader ? reinterpret_cast<PFN_vkGetInstanceProcAddr>(GetProcAddress(loader, "vkGetInstanceProcAddr")) : nullptr;
#else
        auto loader = dlopen("libvulkan.so", RTLD_NOW | RTLD_LOCAL);
        auto get = loader ? reinterpret_cast<PFN_vkGetInstanceProcAddr>(dlsym(loader, "vkGetInstanceProcAddr")) : nullptr;
#endif
        if (get) {
            auto props = reinterpret_cast<PFN_vkGetPhysicalDeviceProperties>(get(instance, "vkGetPhysicalDeviceProperties"));
            if (props) {
                VkPhysicalDeviceProperties p{};
                props(physical, &p);
                std::printf("gpu: %s; vendor: %u; device: %u; api: %u; driver: %u\n", p.deviceName, p.vendorID, p.deviceID, p.apiVersion, p.driverVersion);
            }
        }
#ifdef _WIN32
        if (loader) FreeLibrary(loader);
#else
        if (loader) dlclose(loader);
#endif
    }
    ~Device() { if (value) pyrowave_device_destroy(value); }
};
struct Encoder { pyrowave_encoder value = nullptr; ~Encoder() { if (value) pyrowave_encoder_destroy(value); } };
struct Decoder { pyrowave_decoder value = nullptr; ~Decoder() { if (value) pyrowave_decoder_destroy(value); } };
struct Frame {
    int width, height, chroma;
    std::vector<uint8_t> planes[3];
    Frame(int w, int h, int c) : width(w), height(h), chroma(c) {
        require(w >= 2 && h >= 2 && w <= 4096 && h <= 4096 && !(w & 1) && !(h & 1), "invalid_dimensions");
        require(c == 420 || c == 444, "invalid_chroma");
        planes[0].resize(size_t(w) * h);
        planes[1].resize(size_t(w) * h / (c == 420 ? 4 : 1));
        planes[2].resize(planes[1].size());
    }
    pyrowave_chroma_subsampling sampling() const { return chroma == 420 ? PYROWAVE_CHROMA_SUBSAMPLING_420 : PYROWAVE_CHROMA_SUBSAMPLING_444; }
    pyrowave_cpu_buffer buffer() {
        pyrowave_cpu_buffer b{};
        b.width = width; b.height = height;
        b.format = chroma == 420 ? PYROWAVE_CPU_BUFFER_FORMAT_YUV420P : PYROWAVE_CPU_BUFFER_FORMAT_YUV444P;
        for (int p = 0; p < 3; ++p) {
            b.data[p] = planes[p].data();
            b.row_stride_in_bytes[p] = p && chroma == 420 ? width / 2 : width;
            b.plane_size_in_bytes[p] = planes[p].size();
        }
        return b;
    }
    void pattern() {
        for (int p = 0; p < 3; ++p) {
            int stride = p && chroma == 420 ? width / 2 : width;
            for (size_t i = 0; i < planes[p].size(); ++i) {
                unsigned x = unsigned(i % stride), y = unsigned(i / stride);
                planes[p][i] = uint8_t((3 * x + 5 * y + 37 * p + ((x / 17 ^ y / 11) & 1) * 61) & 255);
            }
        }
    }
};
static void write32(std::ostream &out, uint32_t v) {
    for (unsigned i = 0; i < 4; ++i) out.put(char(v >> (8 * i)));
}
static uint32_t read32(std::istream &in) {
    uint32_t result = 0;
    for (unsigned i = 0; i < 4; ++i) { int b = in.get(); require(b != EOF, "truncated_header"); result |= uint32_t(b) << (8 * i); }
    return result;
}
using Packets = std::vector<std::vector<uint8_t>>;
static Packets encode(Device &device, Frame &frame) {
    Encoder encoder;
    pyrowave_encoder_create_info info{device.value, frame.width, frame.height, frame.sampling()};
    check(pyrowave_encoder_create(&info, &encoder.value), "encoder_create");
    auto buffer = frame.buffer();
    constexpr size_t budget = 64 * 1024 * 1024;
    constexpr size_t boundary = 256 * 1024;
    pyrowave_rate_control rate{budget};
    check(pyrowave_encoder_encode_cpu_synchronous(encoder.value, &buffer, &rate), "encode_submit");
    size_t count = 0;
    check(pyrowave_encoder_compute_num_packets(encoder.value, boundary, &count), "packet_count");
    require(count && count <= 1024, "invalid_packet_count");
    std::vector<pyrowave_packet> ranges(count);
    std::vector<uint8_t> bytes(count * boundary);
    size_t capacity = count;
    check(pyrowave_encoder_packetize(encoder.value, ranges.data(), boundary, &count, bytes.data(), bytes.size()), "packetize");
    require(count <= capacity, "packet_count_overflow");
    Packets packets;
    for (size_t i = 0; i < count; ++i) {
        const auto &r = ranges[i];
        require(r.offset <= bytes.size() && r.size && r.size <= bytes.size() - r.offset, "invalid_packet_range");
        packets.emplace_back(bytes.begin() + r.offset, bytes.begin() + r.offset + r.size);
    }
    return packets;
}
static void decode(Device &device, Frame &frame, const Packets &packets, const std::string &path) {
    Decoder decoder;
    bool fragment = path == "fragment" || (path == "auto" && pyrowave_decoder_device_prefers_fragment_path(device.value));
    require(path == "auto" || path == "fragment" || path == "compute", "invalid_decode_path");
    std::printf("decoder_path: %s\n", fragment ? "fragment" : "compute");
    pyrowave_decoder_create_info info{device.value, frame.width, frame.height, frame.sampling(), fragment};
    check(pyrowave_decoder_create(&info, &decoder.value), "decoder_create");
    for (const auto &packet : packets) check(pyrowave_decoder_push_packet(decoder.value, packet.data(), packet.size()), "push_packet");
    require(pyrowave_decoder_decode_is_ready(decoder.value, false), "decoder_not_ready");
    auto buffer = frame.buffer();
    check(pyrowave_decoder_decode_cpu_buffer_synchronous(decoder.value, &buffer), "decode_readback");
}
int main(int argc, char **argv) {
    std::thread([] {
        std::this_thread::sleep_for(std::chrono::seconds(30));
        std::fputs("FAIL: watchdog_timeout\n", stderr);
        std::_Exit(3);
    }).detach();
    try {
        require(argc >= 2, "Usage: probe | encode fixture golden width height 420|444 | decode fixture golden [auto|compute|fragment]");
        Device device;
        std::string mode = argv[1];
        if (mode == "probe") {
            Frame frame(128, 72, 420);
            Decoder decoder;
            pyrowave_decoder_create_info d{device.value, 128, 72, frame.sampling(), pyrowave_decoder_device_prefers_fragment_path(device.value)};
            check(pyrowave_decoder_create(&d, &decoder.value), "decoder_create");
            Encoder encoder;
            pyrowave_encoder_create_info e{device.value, 128, 72, frame.sampling()};
            // Deliberately separate encoder failure from decoder initialization.
            check(pyrowave_encoder_create(&e, &encoder.value), "encoder_create");
        } else if (mode == "encode") {
            require(argc == 7, "encode_arguments");
            Frame source(std::stoi(argv[4]), std::stoi(argv[5]), std::stoi(argv[6]));
            source.pattern();
            auto packets = encode(device, source);
            Frame golden(source.width, source.height, source.chroma);
            decode(device, golden, packets, "auto");
            for (int p = 0; p < 3; ++p) {
                int maximum = 0;
                for (size_t i = 0; i < source.planes[p].size(); ++i)
                    maximum = std::max(maximum, std::abs(int(source.planes[p][i]) - int(golden.planes[p][i])));
                std::printf("host_source_plane_%d_max_error: %d\n", p, maximum);
                require(maximum <= (p == 0 ? 1 : 2), "host_reference_outside_source_tolerance");
            }
            std::ofstream fixture(argv[2], std::ios::binary), reference(argv[3], std::ios::binary);
            fixture.write("RFPW0001", 8);
            write32(fixture, source.width); write32(fixture, source.height); write32(fixture, source.chroma); write32(fixture, uint32_t(packets.size()));
            for (const auto &packet : packets) { write32(fixture, uint32_t(packet.size())); fixture.write(reinterpret_cast<const char *>(packet.data()), packet.size()); }
            for (const auto &plane : golden.planes) reference.write(reinterpret_cast<const char *>(plane.data()), plane.size());
            require(bool(fixture) && bool(reference), "fixture_write_failed");
        } else if (mode == "decode") {
            require(argc == 4 || argc == 5, "decode_arguments");
            std::ifstream fixture(argv[2], std::ios::binary), reference(argv[3], std::ios::binary);
            char magic[8]{}; fixture.read(magic, 8);
            require(fixture && std::memcmp(magic, "RFPW0001", 8) == 0, "invalid_fixture_magic");
            int w = int(read32(fixture)), h = int(read32(fixture)), c = int(read32(fixture));
            Frame output(w, h, c);
            unsigned count = read32(fixture);
            require(count && count <= 1024, "invalid_packet_count");
            Packets packets;
            size_t total = 0;
            for (unsigned p = 0; p < count; ++p) {
                unsigned size = read32(fixture); total += size;
                require(size && size <= 16 * 1024 * 1024 && total <= 64 * 1024 * 1024, "fixture_too_large");
                packets.emplace_back(size);
                fixture.read(reinterpret_cast<char *>(packets.back().data()), size);
                require(bool(fixture), "truncated_packet");
            }
            require(fixture.peek() == EOF, "trailing_fixture_data");
            decode(device, output, packets, argc == 5 ? argv[4] : "auto");
            size_t bad = 0;
            for (int p = 0; p < 3; ++p) {
                int maxError = 0;
                for (uint8_t value : output.planes[p]) {
                    int expected = reference.get(); require(expected != EOF, "truncated_golden");
                    int error = std::abs(int(value) - expected);
                    maxError = std::max(maxError, error);
                    if (error > (p == 0 ? 1 : 2)) ++bad;
                }
                std::printf("plane_%d_max_error: %d\n", p, maxError);
            }
            require(reference.peek() == EOF, "trailing_golden_data");
            std::printf("samples_outside_tolerance: %zu\n", bad);
            require(!bad, "golden_comparison_failed");
        } else throw std::runtime_error("unknown_mode");
        std::puts("PASS");
        return 0;
    } catch (const std::exception &error) {
        std::fprintf(stderr, "FAIL: %s\n", error.what());
        return 1;
    }
}
