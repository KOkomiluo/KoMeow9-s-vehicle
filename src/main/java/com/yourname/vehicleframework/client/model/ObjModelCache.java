package com.yourname.vehicleframework.client.model;

import com.yourname.vehicleframework.VehicleFramework;

import net.minecraft.resources.ResourceLocation;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * OBJ 模型缓存。
 * <p>
 * 避免每次渲染都重新加载和解析 OBJ 文件。
 * 资源包重载时自动清除缓存。
 */
public final class ObjModelCache {

    private ObjModelCache() {}

    private static final Map<ResourceLocation, ObjModel> CACHE = new ConcurrentHashMap<>();

    /**
     * 获取或加载 OBJ 模型。
     *
     * @param location 模型资源位置
     * @return 缓存的模型对象，加载失败返回 null
     */
    public static ObjModel get(ResourceLocation location) {
        return CACHE.computeIfAbsent(location, ObjLoader::load);
    }

    /**
     * 通过路径字符串获取模型（自动添加命名空间前缀）。
     *
     * @param path 模型路径，如 "models/obj/sports_car.obj"
     * @return 缓存的模型对象
     */
    public static ObjModel get(String path) {
        ResourceLocation location = new ResourceLocation(VehicleFramework.MOD_ID, path);
        return get(location);
    }

    /**
     * 清除所有缓存。在资源包重载时调用。
     */
    public static void clear() {
        CACHE.clear();
    }

    /**
     * 预加载指定模型。用于启动时提前加载避免游戏中卡顿。
     */
    public static void preload(String... paths) {
        for (String path : paths) {
            get(path);
        }
    }
}
