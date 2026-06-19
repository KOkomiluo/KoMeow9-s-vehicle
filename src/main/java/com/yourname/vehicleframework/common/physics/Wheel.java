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

    // ── 轮胎物理状态（P0 摩擦圆模型）──
    /** 当前纵向滑移率 σ = (ωr - vx) / max(|ωr|, |vx|)。0=纯滚动，+1=完全空转，-1=完全锁死。 */
    public double slipRatio;
    /** 当前侧滑角 α（弧度）。0=无侧滑，越大横向力越大。 */
    public double slipAngle;
    /** 当前轮胎法向载荷 Fz（N，经重心转移调整后）。 */
    public double normalLoad;
    /** 当前 tick 产生的纵向力 Fx（N，正值=驱动，负值=制动）。 */
    public double longitudinalForce;
    /** 当前 tick 产生的横向力 Fy（N）。 */
    public double lateralForce;
    /** 车轮表面线速度（m/s 或 blocks/tick 等效），持久跨 tick。 */
    public double wheelRotationSpeed;
    /** 上一 tick 的轮速（用于计算轮加速度）。 */
    public double prevWheelRotationSpeed;

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
        this.slipRatio = 0;
        this.slipAngle = 0;
        this.normalLoad = 0;
        this.longitudinalForce = 0;
        this.lateralForce = 0;
        this.wheelRotationSpeed = 0;
        this.prevWheelRotationSpeed = 0;
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

    /** 推进悬挂状态和轮速到下一 tick。 */
    public void advanceState() {
        this.prevSuspensionLength = this.suspensionLength;
        this.prevWheelRotationSpeed = this.wheelRotationSpeed;
    }

    /**
     * 将世界速度分解为车轮局部坐标系下的前向/侧向分量。
     *
     * @param worldVelocity 车辆世界速度
     * @param vehicleYawRad 车辆朝向（弧度）
     * @return [forwardSpeed, lateralSpeed] — 前向为正=前进，侧向为正=右侧
     */
    public double[] getLocalVelocity(Vec3 worldVelocity, float vehicleYawRad) {
        // 车轮方向 = 车辆朝向 + 转向偏角（仅前轮转向）
        double wheelYaw = vehicleYawRad + Math.toRadians(this.steerAngle);
        double cos = Math.cos(wheelYaw);
        double sin = Math.sin(wheelYaw);
        double forwardSpeed =  worldVelocity.x * (-sin) + worldVelocity.z * cos;
        double lateralSpeed = worldVelocity.x * cos + worldVelocity.z * sin;
        return new double[]{forwardSpeed, lateralSpeed};
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
