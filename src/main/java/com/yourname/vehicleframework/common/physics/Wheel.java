package com.yourname.vehicleframework.common.physics;

import net.minecraft.world.phys.Vec3;

/**
 * 车轮数据类。
 * <p>
 * 存储车轮的配置参数和运行时物理状态。
 * 每个车辆实体拥有 4 个 Wheel 实例（FL/FR/RL/RR）。
 * <p>
 * <b>坐标约定</b>（MC 世界坐标系，Y 轴向上）：
 * <ul>
 *   <li>localPos.x &gt; 0 → 车辆右侧（left=false）</li>
 *   <li>localPos.z &gt; 0 → 车辆前方（前轮）</li>
 *   <li>localPos.y = 车轮中心相对于车辆中心的垂直偏移</li>
 * </ul>
 */
public class Wheel {

    // ── 配置常量 ──
    /** 悬挂最大伸展长度（从车轮中心向下探测的最远距离）。 */
    public static final double MAX_SUSPENSION_LENGTH = 0.8;
    /** 悬挂静止长度（无负载时车轮到车身的距离）。 */
    public static final double REST_SUSPENSION_LENGTH = 0.4;
    /** 悬挂最小压缩长度（完全压缩时的硬限制）。 */
    public static final double MIN_SUSPENSION_LENGTH = 0.05;

    // ── 配置参数 ──
    /** 车轮相对于车辆中心的局部坐标。 */
    public final Vec3 localPos;
    /** 是否为前轮。 */
    public final boolean isFront;
    /** 是否为左侧车轮。 */
    public final boolean isLeft;
    /** 车轮半径（方块单位）。 */
    public final double radius;

    // ── 运行时物理状态 ──
    /** 当前悬挂压缩长度（0 = 完全伸展，值越大压缩越多）。 */
    public double suspensionLength;
    /** 上一 tick 的悬挂长度（用于计算压缩速度）。 */
    public double prevSuspensionLength;
    /** 当前 tick 是否着地。 */
    public boolean grounded;
    /** 着地点世界坐标（仅当 grounded=true 时有效）。 */
    public Vec3 groundPoint;
    /** 地面法线方向（仅当 grounded=true 时有效）。 */
    public Vec3 groundNormal;
    /** 车轮角速度（rad/tick，用于 ABS 检测）。 */
    public double wheelAngularVelocity;
    /** 当前转向偏角（仅前轮有效，度数）。 */
    public double steerAngle;

    public Wheel(Vec3 localPos, boolean isFront, boolean isLeft, double radius) {
        this.localPos = localPos;
        this.isFront = isFront;
        this.isLeft = isLeft;
        this.radius = radius;
        this.suspensionLength = REST_SUSPENSION_LENGTH;
        this.prevSuspensionLength = REST_SUSPENSION_LENGTH;
        this.grounded = false;
        this.groundPoint = Vec3.ZERO;
        this.groundNormal = new Vec3(0, 1, 0);
        this.wheelAngularVelocity = 0;
        this.steerAngle = 0;
    }

    /** 获取当前悬挂压缩速度（方块/tick，正值=正在压缩）。 */
    public double getCompressionVelocity() {
        return suspensionLength - prevSuspensionLength;
    }

    /** 获取悬挂压缩比例（0=完全伸展, 1=完全压缩）。 */
    public double getCompressionRatio() {
        return Math.max(0, Math.min(1,
                (suspensionLength - MIN_SUSPENSION_LENGTH)
                        / (MAX_SUSPENSION_LENGTH - MIN_SUSPENSION_LENGTH)));
    }

    /** 推进悬挂状态到下一 tick。 */
    public void advanceState() {
        this.prevSuspensionLength = this.suspensionLength;
    }

    /** 设置着地状态。 */
    public void setGrounded(boolean grounded, Vec3 point, Vec3 normal) {
        this.grounded = grounded;
        if (grounded) {
            this.groundPoint = point;
            this.groundNormal = normal;
        } else {
            this.groundPoint = Vec3.ZERO;
            this.groundNormal = new Vec3(0, 1, 0);
        }
    }

    @Override
    public String toString() {
        String prefix = isFront ? "F" : "R";
        String side = isLeft ? "L" : "R";
        return String.format("Wheel[%s%s grounded=%b susp=%.3f steer=%.1f°]",
                prefix, side, grounded, suspensionLength, steerAngle);
    }
}
