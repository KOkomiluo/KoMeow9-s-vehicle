package com.yourname.vehicleframework.data;

/**
 * 车辆类型数据类（record）。
 * <p>
 * 从 JSON 配置文件映射而来的车辆属性。
 * 所有车辆属性均由数据驱动，通过 JSON 文件配置。
 * <p>
 * JSON 文件路径：data/vehicleframework/vehicles/*.json
 * <p>
 * 模型渲染方式：
 * - 若 objModelPath 非空 → 使用 OBJ 模型渲染器（从 assets/models/obj/ 加载 .obj）
 * - 若 objModelPath 为空 → 使用 GeckoLib 骨骼动画渲染器
 *
 * @param objModelPath OBJ 模型资源路径 (如 "models/obj/sports_car.obj")，空字符串表示使用 GeckoLib
 * @param objScale     OBJ 模型缩放比例，默认 1/16（Blender 1 单位 = MC 1/16 方块）
 */
public record VehicleType(
        String id,
        String displayName,
        double maxSpeed,
        double acceleration,
        double brakingPower,
        double handling,
        double fuelCapacity,
        int    seatCount,
        String modelPath,
        String texturePath,
        String animationPath,
        double weight,
        String hornSound,
        String objModelPath,
        double objScale
) {
    /** 默认车辆类型（回退用，使用 GeckoLib 渲染）。 */
    public static final VehicleType DEFAULT = new VehicleType(
            "sports_car", "Sports Car",
            1.0, 0.015, 0.04, 1.0,
            100.0, 2,
            "vehicle", "sports_car", "vehicle",
            1500.0, "vehicleframework:horn.default",
            "", 0.0625  // 默认 1/16 缩放
    );

    /** 是否使用 OBJ 模型渲染（而非 GeckoLib）。 */
    public boolean useObjModel() {
        return objModelPath != null && !objModelPath.isEmpty();
    }

    /** 获取有效的缩放值（0 或负数时回退到默认值）。 */
    public double getEffectiveScale() {
        return objScale > 0 ? objScale : 0.0625;
    }

    /** 检查此配置是否有效。 */
    public boolean isValid() {
        return id != null && !id.isEmpty()
                && maxSpeed > 0
                && seatCount >= 1
                && fuelCapacity > 0;
    }
}
