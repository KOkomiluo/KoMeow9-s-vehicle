package com.yourname.vehicleframework.data;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;

import com.yourname.vehicleframework.VehicleFramework;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.packs.resources.ResourceManager;

import org.slf4j.Logger;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;

/**
 * 车辆配置加载器。
 * <p>
 * 从 data/vehicleframework/vehicles/ 目录下读取所有 .json 文件，
 * 解析为 VehicleType 对象并缓存在内存中。
 */
public final class VehicleConfigLoader {

    private VehicleConfigLoader() {}

    private static final Logger LOGGER = VehicleFramework.LOGGER;
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final String VEHICLES_DIR = "vehicles";

    private static final Map<String, VehicleType> CONFIGS = new HashMap<>();

    public static void loadAllConfigs(MinecraftServer server) {
        CONFIGS.clear();
        ResourceManager resourceManager = server.getResourceManager();

        resourceManager.listResources(VEHICLES_DIR, path ->
                path.getPath().endsWith(".json")).forEach((id, resource) -> {
            try (InputStream stream = resource.open();
                 InputStreamReader reader = new InputStreamReader(stream, StandardCharsets.UTF_8)) {

                JsonElement json = GSON.fromJson(reader, JsonElement.class);
                VehicleType type = parseVehicleType(id, json);

                if (type != null && type.isValid()) {
                    CONFIGS.put(type.id(), type);
                    LOGGER.info("Loaded vehicle config: {} ({})", type.id(), type.displayName());
                } else {
                    LOGGER.warn("Invalid vehicle config: {}", id);
                }

            } catch (IOException e) {
                LOGGER.error("Failed to load vehicle config: {}", id, e);
            }
        });

        LOGGER.info("Loaded {} vehicle configs.", CONFIGS.size());
    }

    private static VehicleType parseVehicleType(ResourceLocation id, JsonElement json) {
        try {
            String path = id.getPath();
            String vehicleId = path.substring(path.lastIndexOf('/') + 1).replace(".json", "");
            var obj = json.getAsJsonObject();

            return new VehicleType(
                    getOrDefault(obj, "id", vehicleId),
                    getOrDefault(obj, "displayName", vehicleId),
                    getDoubleOrDefault(obj, "maxSpeed", 1.0),
                    getDoubleOrDefault(obj, "acceleration", 0.015),
                    getDoubleOrDefault(obj, "brakingPower", 0.04),
                    getDoubleOrDefault(obj, "handling", 1.0),
                    getDoubleOrDefault(obj, "fuelCapacity", 100.0),
                    getIntOrDefault(obj, "seatCount", 2),
                    getOrDefault(obj, "modelPath", "vehicle"),
                    getOrDefault(obj, "texturePath", vehicleId),
                    getOrDefault(obj, "animationPath", "vehicle"),
                    getDoubleOrDefault(obj, "weight", 1500.0),
                    getOrDefault(obj, "hornSound", "vehicleframework:horn.default"),
                    getOrDefault(obj, "objModelPath", ""),
                    getDoubleOrDefault(obj, "objScale", 0.0625),
                    // ── 新增物理配置字段 ──
                    getDoubleArrayOrDefault(obj, "gearRatios", VehicleType.DEFAULT_GEAR_RATIOS),
                    getDoubleOrDefault(obj, "finalDriveRatio", 3.5),
                    getDoubleOrDefault(obj, "springStiffness", 50.0),
                    getDoubleOrDefault(obj, "damperCoefficient", 8.0),
                    getDoubleOrDefault(obj, "corneringStiffness", 4.0),
                    getDoubleOrDefault(obj, "enginePeakTorque", 300.0),
                    getDoubleOrDefault(obj, "enginePeakRPM", 4000.0),
                    getDoubleOrDefault(obj, "transmissionEfficiency", 0.9),
                    getOrDefault(obj, "driveType", "rwd")
            );
        } catch (Exception e) {
            LOGGER.error("Failed to parse vehicle type from: {}", id, e);
            return null;
        }
    }

    private static String getOrDefault(com.google.gson.JsonObject obj, String key, String def) {
        return obj.has(key) ? obj.get(key).getAsString() : def;
    }

    private static double getDoubleOrDefault(com.google.gson.JsonObject obj, String key, double def) {
        return obj.has(key) ? obj.get(key).getAsDouble() : def;
    }

    private static int getIntOrDefault(com.google.gson.JsonObject obj, String key, int def) {
        return obj.has(key) ? obj.get(key).getAsInt() : def;
    }

    private static double[] getDoubleArrayOrDefault(com.google.gson.JsonObject obj, String key, double[] def) {
        if (!obj.has(key)) return def;
        try {
            JsonArray arr = obj.getAsJsonArray(key);
            double[] result = new double[arr.size()];
            for (int i = 0; i < arr.size(); i++) {
                result[i] = arr.get(i).getAsDouble();
            }
            return result.length > 0 ? result : def;
        } catch (Exception e) {
            LOGGER.warn("Failed to parse '{}' as double array, using default", key);
            return def;
        }
    }

    public static VehicleType getConfig(String id) {
        return CONFIGS.getOrDefault(id, VehicleType.DEFAULT);
    }

    public static Map<String, VehicleType> getAllConfigs() {
        return Map.copyOf(CONFIGS);
    }

    public static boolean hasConfig(String id) {
        return CONFIGS.containsKey(id);
    }
}
