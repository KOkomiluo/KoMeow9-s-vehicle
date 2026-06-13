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
 * <p>
 * <b>物理参数</b>（新增字段，均有合理默认值）：
 * <ul>
 *   <li>gearRatios — 挡位传动比 [R, N, 1, 2, 3, 4, 5, 6]</li>
 *   <li>finalDriveRatio — 主减速比</li>
 *   <li>springStiffness — 悬挂弹簧刚度 k</li>
 *   <li>damperCoefficient — 悬挂阻尼系数 c</li>
 *   <li>corneringStiffness — 轮胎侧向刚度 Cα</li>
 *   <li>enginePeakTorque — 发动机峰值扭矩 (N·m)</li>
 *   <li>enginePeakRPM — 峰值扭矩转速</li>
 *   <li>transmissionEfficiency — 传动效率 η (0~1)</li>
 *   <li>driveType — 驱动方式 "rwd"/"fwd"/"awd"</li>
 * </ul>
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
        double objScale,
        // ── 新增物理配置字段 ──
        double[] gearRatios,
        double finalDriveRatio,
        double springStiffness,
        double damperCoefficient,
        double corneringStiffness,
        double enginePeakTorque,
        double enginePeakRPM,
        double transmissionEfficiency,
        String driveType
) {
    /** 默认挡位传动比：R, N, 1, 2, 3, 4, 5, 6 */
    public static final double[] DEFAULT_GEAR_RATIOS = {
            -3.5,   // R (倒挡)
             0.0,   // N (空挡)
             3.5,   // 1 挡
             2.2,   // 2 挡
             1.5,   // 3 挡
             1.1,   // 4 挡
             0.85,  // 5 挡
             0.7    // 6 挡
    };

    /** 默认车辆类型（回退用，使用 GeckoLib 渲染）。 */
    public static final VehicleType DEFAULT = new VehicleType(
            "sports_car", "Sports Car",
            1.0, 0.015, 0.04, 1.0,
            100.0, 2,
            "vehicle", "sports_car", "vehicle",
            1500.0, "vehicleframework:horn.default",
            "", 0.0625,
            DEFAULT_GEAR_RATIOS, 3.5, 50.0, 8.0,
            4.0, 300.0, 4000.0, 0.9, "rwd"
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

    /** 获取当前挡位的传动比。gear=-1→倒挡, gear=0→空挡, gear=1~6→1~6挡 */
    public double getGearRatio(int gear) {
        int index = gear + 1; // gear=-1 → index=0 (R), gear=0 → index=1 (N), gear=1 → index=2, ...
        if (index < 0 || index >= gearRatios.length) return 0;
        return gearRatios[index];
    }

    /** 获取有效挡位传动比（R/N/1-6）。未提供时使用默认值。 */
    public double[] getEffectiveGearRatios() {
        return gearRatios != null && gearRatios.length >= 8 ? gearRatios : DEFAULT_GEAR_RATIOS;
    }

    /** 获取有效的主减速比。 */
    public double getEffectiveFinalDriveRatio() {
        return finalDriveRatio > 0 ? finalDriveRatio : 3.5;
    }

    /** 是否为后轮驱动。 */
    public boolean isRearWheelDrive() {
        return "rwd".equalsIgnoreCase(driveType);
    }

    /** 是否为前轮驱动。 */
    public boolean isFrontWheelDrive() {
        return "fwd".equalsIgnoreCase(driveType);
    }

    /** 是否为全轮驱动。 */
    public boolean isAllWheelDrive() {
        return "awd".equalsIgnoreCase(driveType);
    }

    /** 是否为驱动轮（根据驱动方式判断）。 */
    public boolean isDrivenWheel(boolean isFront) {
        return switch (driveType != null ? driveType.toLowerCase() : "rwd") {
            case "fwd" -> isFront;
            case "awd" -> true;
            default -> !isFront; // rwd
        };
    }
}
