package com.yourname.vehicleframework.common.physics;

import com.yourname.vehicleframework.common.entity.VehicleEntity;
import com.yourname.vehicleframework.data.VehicleType;

import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

/**
 * 车辆物理引擎（MTS 风格重构）。
 * <p>
 * 核心思路（仿 MinecraftTransportSimulator）：
 * <ul>
 *   <li><b>转弯</b>：kinematic 模型 — turningForce = (steerAngle / wheelbase) * velocity
 *       高速时按指数衰减。直接修改 yaw 角度，不用力矩/惯性计算。</li>
 *   <li><b>抓地力</b>：计算 motion 和 heading 的夹角 vectorDelta，
 *       根据轮胎抓地力把 motion 朝 heading 方向拉回。这就是"不侧滑"的本质。</li>
 *   <li><b>漂移</b>：手刹时后轮抓地力降低 → motion 不容易对齐 heading → 侧滑产生。</li>
 *   <li><b>驱动/制动</b>：力 → 加速度 → speed 标量。</li>
 *   <li><b>悬挂</b>：射线检测 + 弹簧-阻尼（不变）。</li>
 *   <li><b>引擎</b>：惯性模型 + 扭矩曲线 + 自动换挡。</li>
 * </ul>
 */
public final class VehiclePhysicsEngine {

    private VehiclePhysicsEngine() {}

    // ── 物理常量 ──
    private static final double SPRING_STIFFNESS = 40.0;
    private static final double DAMPER_COEFFICIENT = 350.0;
    private static final double MAX_COMPRESSION_VEL = 0.3;
    private static final double MAX_STEERING_ANGLE = 35.0;
    private static final double STEERING_RATE = 3.5;
    private static final double IDLE_RPM = 800;
    private static final double MAX_RPM = 7500;
    private static final double FUEL_BASE_CONSUMPTION = 0.008;
    private static final double FUEL_ACCEL_FACTOR = 0.015;
    private static final double ANTI_JITTER_SPEED = 0.03;

    // ── 自动换挡 ──
    private static final double UPSHIFT_RPM = 5200;
    private static final double DOWNSHIFT_RPM = 1800;

    // ── 力缩放 ──
    private static final double FORCE_SCALE = 0.006;
    private static final double GRAVITY = 0.08;
    /** 轮胎基础抓地力（对应 MTS 的 lateralFriction）。 */
    private static final double TIRE_GRIP = 0.8;
    /** 手刹时后轮抓地力倍率（漂移用）。 */
    private static final double HANDBRAKE_REAR_GRIP = 0.2;
    /** 高速转向力衰减起点。 */
    private static final double STEER_HIGH_SPEED_THRESHOLD = 0.35;
    /** 高速转向力衰减因子。 */
    private static final double STEER_HIGH_SPEED_DECAY = 0.3;

    // ═══════════════════════════════════════════════════════════════
    //  主入口
    // ═══════════════════════════════════════════════════════════════

    public static void applyPhysics(VehicleEntity vehicle, Level level) {
        VehicleType type = vehicle.getVehicleTypeConfig();
        if (type == null) type = VehicleType.DEFAULT;

        // 燃料耗尽 → 仅自然减速
        if (vehicle.getFuel() <= 0 && Math.abs(vehicle.getSpeed()) < 0.001) {
            applyNaturalDeceleration(vehicle);
            applyMovement(vehicle);
            return;
        }

        advanceWheelStates(vehicle);
        updateSuspension(vehicle, level);
        updateEngineRPM(vehicle, type);
        autoGearShift(vehicle);

        // ★ MTS 风格：转弯 + 抓地力 + 驱动 + 制动
        applyDriving(vehicle, type);
        applySteeringAndGrip(vehicle, type);

        // 运动合成 + 后处理
        applyMovement(vehicle);
        applyAntiJitter(vehicle);
        consumeFuel(vehicle);
    }

    // ═══════════════════════════════════════════════════════════════
    //  1. 自动变速箱
    // ═══════════════════════════════════════════════════════════════

    private static void autoGearShift(VehicleEntity vehicle) {
        int gear = vehicle.getGear();
        double speed = Math.abs(vehicle.getSpeed());

        if (speed < 0.05 && vehicle.isBraking() && gear > -1) {
            vehicle.shiftGear(false);
            return;
        }
        if (gear <= 0 && vehicle.isAccelerating()) {
            vehicle.setGear(1);
            return;
        }
        if (gear <= 0) return;

        double rpm = vehicle.getEngineRPM();
        if (rpm > UPSHIFT_RPM && gear < 6 && vehicle.isAccelerating()) {
            vehicle.shiftGear(true);
        }
        if (rpm < DOWNSHIFT_RPM && gear > 1 && speed < 0.3) {
            vehicle.shiftGear(false);
        }
    }

    // ═══════════════════════════════════════════════════════════════
    //  2. 悬挂系统
    // ═══════════════════════════════════════════════════════════════

    private static void updateSuspension(VehicleEntity vehicle, Level level) {
        Wheel[] wheels = vehicle.getWheels();
        if (wheels == null) return;

        for (Wheel wheel : wheels) {
            Vec3 worldWheelPos = getWorldWheelPos(vehicle, wheel);
            Vec3 rayStart = worldWheelPos.add(0, Wheel.MAX_SUSPENSION_LENGTH * 0.5, 0);
            Vec3 rayEnd = worldWheelPos.subtract(0, Wheel.MAX_SUSPENSION_LENGTH + wheel.radius, 0);

            ClipContext ctx = new ClipContext(rayStart, rayEnd,
                    ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, vehicle);
            BlockHitResult hit = level.clip(ctx);

            if (hit.getType() == HitResult.Type.BLOCK) {
                double groundDist = worldWheelPos.y - hit.getLocation().y;
                double effectiveLength = groundDist - wheel.radius;
                double compression = Wheel.REST_SUSPENSION_LENGTH - effectiveLength;
                compression = Math.max(-Wheel.MAX_SUSPENSION_LENGTH * 0.3,
                                      Math.min(compression, Wheel.REST_SUSPENSION_LENGTH - Wheel.MIN_SUSPENSION_LENGTH));

                double compressionVel = compression - wheel.prevSuspensionLength;
                compressionVel = Math.max(-MAX_COMPRESSION_VEL, Math.min(compressionVel, MAX_COMPRESSION_VEL));
                double springForce = SPRING_STIFFNESS * compression;
                double damperForce = DAMPER_COEFFICIENT * compressionVel;
                double totalForce = springForce + damperForce;

                double mass = Math.max(1, vehicle.getVehicleWeight());
                Vec3 currentDM = vehicle.getDeltaMovement();
                vehicle.setDeltaMovement(currentDM.x, currentDM.y + totalForce / mass, currentDM.z);

                wheel.suspensionLength = compression;
                net.minecraft.core.Direction face = hit.getDirection();
                Vec3 normal = new Vec3(face.getStepX(), face.getStepY(), face.getStepZ());
                wheel.setGrounded(true, hit.getLocation(), normal);
            } else {
                wheel.suspensionLength = -Wheel.REST_SUSPENSION_LENGTH * 0.3;
                wheel.setGrounded(false, Vec3.ZERO, new Vec3(0, 1, 0));
            }
        }
    }

    // ═══════════════════════════════════════════════════════════════
    //  3. 发动机 RPM + 惯性
    // ═══════════════════════════════════════════════════════════════

    private static void updateEngineRPM(VehicleEntity vehicle, VehicleType type) {
        double rpm = vehicle.getEngineRPM();
        double speed = Math.abs(vehicle.getSpeed());
        double wheelRadius = 0.4;
        int gear = vehicle.getGear();
        boolean accel = vehicle.isAccelerating();
        double J = type.engineInertia() > 0 ? type.engineInertia() : 0.15;
        double brakeTorque = type.engineBrakingTorque() > 0 ? type.engineBrakingTorque() : 50.0;

        if (gear == 0) {
            if (accel) {
                double T_engine = torqueCurve(rpm, type.enginePeakTorque(), type.enginePeakRPM());
                double rpmDelta = T_engine / J * 60.0 / (2.0 * Math.PI);
                rpm = Math.max(IDLE_RPM * 0.5, Math.min(rpm + rpmDelta, MAX_RPM));
            } else {
                rpm = moveToward(rpm, IDLE_RPM, 80 / J);
            }
        } else {
            double gearRatio = type.getGearRatio(gear);
            double finalDrive = type.getEffectiveFinalDriveRatio();
            double wheelAngVel = speed / wheelRadius;
            double targetRPM = wheelAngVel * Math.abs(gearRatio) * finalDrive * (60.0 / (2.0 * Math.PI));
            targetRPM = Math.max(IDLE_RPM * 0.6, Math.min(targetRPM, MAX_RPM));

            double T_engine = accel ? torqueCurve(rpm, type.enginePeakTorque(), type.enginePeakRPM()) : 0;
            if (!accel && gearRatio > 0) {
                T_engine = -brakeTorque * (rpm / IDLE_RPM) * 0.3;
            }
            double rpmError = targetRPM - rpm;
            double T_load = rpmError * J * 0.1;
            double rpmDelta = (T_engine + T_load) / J * 60.0 / (2.0 * Math.PI);
            rpmDelta = Math.max(-300, Math.min(rpmDelta, 300));
            rpm = Math.max(IDLE_RPM * 0.5, Math.min(rpm + rpmDelta, MAX_RPM));
        }
        vehicle.setEngineRPM(rpm);
    }

    // ═══════════════════════════════════════════════════════════════
    //  4. 驱动 + 制动（力 → 加速度 → speed）
    // ═══════════════════════════════════════════════════════════════

    private static void applyDriving(VehicleEntity vehicle, VehicleType type) {
        Wheel[] wheels = vehicle.getWheels();
        double mass = Math.max(1, vehicle.getVehicleWeight());
        double speed = vehicle.getSpeed();
        double wheelRadius = 0.4;
        int gear = vehicle.getGear();
        double gearRatio = gear != 0 ? type.getGearRatio(gear) : 0;

        // 检查驱动轮着地数
        int groundedDriveWheels = 0;
        if (wheels != null) {
            for (Wheel w : wheels) {
                if (w.grounded && type.isDrivenWheel(w.isFront)) groundedDriveWheels++;
            }
        }

        // 驱动力
        if (vehicle.isAccelerating() && vehicle.getFuel() > 0 && gear != 0 && groundedDriveWheels > 0) {
            double engineTorque = torqueCurve(vehicle.getEngineRPM(),
                    type.enginePeakTorque(), type.enginePeakRPM());
            double driveshaftTorque = engineTorque * Math.abs(gearRatio)
                    * type.getEffectiveFinalDriveRatio() * type.transmissionEfficiency();
            double torquePerWheel = driveshaftTorque / groundedDriveWheels;
            double driveForce = torquePerWheel / wheelRadius * FORCE_SCALE;
            if (gearRatio < 0 || gear == -1) driveForce = -driveForce;

            double acceleration = driveForce * groundedDriveWheels / mass;
            double newSpeed = speed + acceleration;
            double maxSpeed = vehicle.getMaxSpeed();
            if (newSpeed < 0) newSpeed = Math.max(newSpeed, -maxSpeed * 0.3);
            else newSpeed = Math.min(newSpeed, maxSpeed);
            vehicle.setSpeed(newSpeed);
        }

        // 制动
        if (vehicle.isBraking() || vehicle.isHandbrakeActive()) {
            double brakePower = 0.06;
            if (vehicle.isHandbrakeActive()) brakePower *= 1.3;
            double speedAbs = Math.abs(speed);
            if (speedAbs > 0.001) {
                double newSpeed;
                if (speed > 0) newSpeed = Math.max(0, speed - brakePower);
                else newSpeed = Math.min(0, speed + brakePower);
                vehicle.setSpeed(newSpeed);
            }
        }
    }

    // ═══════════════════════════════════════════════════════════════
    //  5. 转向 + 抓地力（★ MTS 风格核心）
    // ═══════════════════════════════════════════════════════════════

    private static void applySteeringAndGrip(VehicleEntity vehicle, VehicleType type) {
        Wheel[] wheels = vehicle.getWheels();
        double speed = vehicle.getSpeed();
        float yaw = vehicle.getYRot();
        double yawRad = Math.toRadians(yaw);

        // ── 平滑方向盘角度 ──
        double targetAngle = 0;
        if (vehicle.isSteeringLeft())  targetAngle -= MAX_STEERING_ANGLE;
        if (vehicle.isSteeringRight()) targetAngle += MAX_STEERING_ANGLE;
        double currentAngle = vehicle.getSteeringAngle();
        if (currentAngle < targetAngle) currentAngle = Math.min(currentAngle + STEERING_RATE, targetAngle);
        else if (currentAngle > targetAngle) currentAngle = Math.max(currentAngle - STEERING_RATE, targetAngle);
        else currentAngle = moveToward(currentAngle, 0, STEERING_RATE * 0.7);
        vehicle.setSteeringAngle(currentAngle);
        if (wheels != null) {
            for (Wheel w : wheels) w.steerAngle = w.isFront ? currentAngle : 0;
        }

        // ── 计算 wheelbase ──
        double wheelbase = 3.2;
        if (wheels != null) {
            double minZ = Double.MAX_VALUE, maxZ = -Double.MAX_VALUE;
            for (Wheel w : wheels) {
                minZ = Math.min(minZ, w.localPos.z);
                maxZ = Math.max(maxZ, w.localPos.z);
            }
            wheelbase = Math.max(0.5, Math.abs(maxZ - minZ));
        }

        // ── 检查是否有轮子着地 ──
        int groundedCount = 0;
        if (wheels != null) for (Wheel w : wheels) if (w.grounded) groundedCount++;
        if (groundedCount == 0) return; // 空中不转向

        // ── MTS 风格转向力计算 ──
        // turningForce = (steerAngle / wheelbase) * groundVelocity
        // 高速时按指数衰减
        double groundVelocity = Math.abs(speed);
        double turningForce = 0;
        if (Math.abs(currentAngle) > 0.01 && groundVelocity > 0.01) {
            turningForce = (currentAngle / wheelbase) * groundVelocity;
            if (groundVelocity > STEER_HIGH_SPEED_THRESHOLD) {
                double decay = Math.pow(STEER_HIGH_SPEED_DECAY,
                        (groundVelocity - STEER_HIGH_SPEED_THRESHOLD));
                turningForce *= decay;
            }
            turningForce *= 2.0; // 缩放常数
        }

        // 倒车时反向
        boolean goingInReverse = speed < -0.01;
        if (goingInReverse) turningForce = -turningForce;

        // 应用 yaw 旋转
        vehicle.setYRot(yaw + (float) turningForce);

        // ── 抓地力修正 motion（让 motion 朝 heading 拉回）──
        if (groundVelocity > 0.01) {
            Vec3 heading = new Vec3(-Math.sin(yawRad), 0, Math.cos(yawRad));
            Vec3 motion = vehicle.getDeltaMovement();
            Vec3 groundMotion = new Vec3(motion.x, 0, motion.z);
            double motionLen = groundMotion.length();
            if (motionLen > 0.001) {
                Vec3 motionDir = groundMotion.normalize();

                // motion 和 heading 的夹角（度）
                double dot = motionDir.dot(heading);
                dot = Math.max(-1, Math.min(1, dot));
                Vec3 cross = motionDir.cross(heading);
                double vectorDelta = Math.toDegrees(Math.atan2(cross.y, dot));

                // ── 计算抓地力 ──
                double skiddingFactor = TIRE_GRIP * groundedCount / 4.0;
                if (vehicle.isHandbrakeActive() && wheels != null) {
                    int rearGrounded = 0;
                    for (Wheel w : wheels) if (!w.isFront && w.grounded) rearGrounded++;
                    skiddingFactor -= rearGrounded * TIRE_GRIP * (1 - HANDBRAKE_REAR_GRIP) / 4.0;
                }

                // ── 抓地力把 motion 朝 heading 拉回 ──
                double motionFactor;
                if (vectorDelta > skiddingFactor) {
                    motionFactor = skiddingFactor / vectorDelta;
                } else if (vectorDelta < -skiddingFactor) {
                    motionFactor = -skiddingFactor / vectorDelta;
                } else {
                    motionFactor = 1;
                }

                Vec3 idealHeading = goingInReverse ? heading.scale(-1) : heading;
                Vec3 idealMotion = idealHeading.scale(groundVelocity);

                double newMotionX = idealMotion.x * motionFactor + motion.x * (1 - motionFactor);
                double newMotionZ = idealMotion.z * motionFactor + motion.z * (1 - motionFactor);
                vehicle.setDeltaMovement(newMotionX, motion.y, newMotionZ);
            }
        }
    }

    // ═══════════════════════════════════════════════════════════════
    //  6. 运动合成
    // ═══════════════════════════════════════════════════════════════

    private static void applyMovement(VehicleEntity vehicle) {
        // 同步 deltaMovement 与 speed（speed 是沿 yaw 方向的标量）
        double speed = vehicle.getSpeed();
        float yaw = vehicle.getYRot();
        double yawRad = Math.toRadians(yaw);
        Vec3 heading = new Vec3(-Math.sin(yawRad), 0, Math.cos(yawRad));
        Vec3 currentDM = vehicle.getDeltaMovement();
        // 水平运动 = speed * heading；垂直运动保留（悬挂/重力）
        vehicle.setDeltaMovement(
                heading.x * speed,
                currentDM.y,
                heading.z * speed);
        vehicle.move(net.minecraft.world.entity.MoverType.SELF, vehicle.getDeltaMovement());
    }

    // ═══════════════════════════════════════════════════════════════
    //  7. 自然减速
    // ═══════════════════════════════════════════════════════════════

    private static void applyNaturalDeceleration(VehicleEntity vehicle) {
        if (vehicle.isAccelerating() || vehicle.isBraking()) return;
        double speed = vehicle.getSpeed();
        if (Math.abs(speed) < 0.0005) { vehicle.setSpeed(0); return; }
        speed *= 0.995;
        if (Math.abs(speed) < 0.0005) speed = 0;
        vehicle.setSpeed(speed);
    }

    // ═══════════════════════════════════════════════════════════════
    //  8. 防抖锁定
    // ═══════════════════════════════════════════════════════════════

    private static void applyAntiJitter(VehicleEntity vehicle) {
        if (Math.abs(vehicle.getSpeed()) < ANTI_JITTER_SPEED
                && !vehicle.isAccelerating() && !vehicle.isBraking()
                && !vehicle.isHandbrakeActive()) {
            vehicle.setDeltaMovement(0, vehicle.getDeltaMovement().y, 0);
            vehicle.setSpeed(0);
        }
    }

    // ═══════════════════════════════════════════════════════════════
    //  9. 燃油消耗
    // ═══════════════════════════════════════════════════════════════

    private static void consumeFuel(VehicleEntity vehicle) {
        double consumption = FUEL_BASE_CONSUMPTION;
        if (vehicle.isAccelerating()) {
            double rpmFactor = vehicle.getEngineRPM() / IDLE_RPM;
            consumption += FUEL_ACCEL_FACTOR * Math.abs(vehicle.getSpeed()) * rpmFactor;
        }
        vehicle.setFuel(vehicle.getFuel() - consumption);
    }

    // ═══════════════════════════════════════════════════════════════
    //  扭矩曲线
    // ═══════════════════════════════════════════════════════════════

    private static double torqueCurve(double rpm, double peakTorque, double peakRPM) {
        if (rpm <= 0) return 0;
        if (rpm < peakRPM) return peakTorque * (rpm / peakRPM);
        return peakTorque * peakRPM / rpm;
    }

    // ═══════════════════════════════════════════════════════════════
    //  辅助方法
    // ═══════════════════════════════════════════════════════════════

    private static void advanceWheelStates(VehicleEntity vehicle) {
        Wheel[] wheels = vehicle.getWheels();
        if (wheels == null) return;
        for (Wheel w : wheels) w.advanceState();
    }

    private static Vec3 getWorldWheelPos(VehicleEntity vehicle, Wheel wheel) {
        float yawRad = (float) Math.toRadians(vehicle.getYRot());
        double cos = Math.cos(yawRad), sin = Math.sin(yawRad);
        double wx = wheel.localPos.x * cos - wheel.localPos.z * sin;
        double wz = wheel.localPos.x * sin + wheel.localPos.z * cos;
        return vehicle.position().add(wx, wheel.localPos.y, wz);
    }

    private static double moveToward(double current, double target, double maxDelta) {
        if (current < target) return Math.min(current + maxDelta, target);
        if (current > target) return Math.max(current - maxDelta, target);
        return current;
    }
}
