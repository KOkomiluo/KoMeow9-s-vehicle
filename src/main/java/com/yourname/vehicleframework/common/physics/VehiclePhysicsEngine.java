package com.yourname.vehicleframework.common.physics;

import com.yourname.vehicleframework.common.entity.VehicleEntity;
import com.yourname.vehicleframework.data.VehicleType;

import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

/**
 * 真实车辆物理引擎。
 * <p>
 * 实现完整的车辆动力学，包括：
 * <ul>
 *   <li>4 轮独立射线悬挂（弹簧-阻尼模型）</li>
 *   <li>动力总成模拟（发动机 RPM → 扭矩曲线 → 传动比 → 驱动力）</li>
 *   <li>自动变速箱（根据 RPM 自动升降挡）</li>
 *   <li>前轮转向 + 侧滑摩擦</li>
 *   <li>手刹漂移（降低后轮侧向刚度 60%）</li>
 *   <li>ABS 防抱死制动（50% 占空比交替）</li>
 *   <li>空中姿态控制（所有轮离地时）</li>
 *   <li>低速防抖锁定</li>
 * </ul>
 * <p>
 * 每个服务端 tick 由 VehicleEntity.tick() 调用。
 */
public final class VehiclePhysicsEngine {

    private VehiclePhysicsEngine() {}

    // ── 物理常量 ──
    // 悬挂参数：阻尼比 ζ ≈ 0.7（c=350, k=40, m≈1500）
    // c_crit = 2*sqrt(k*m) ≈ 490 → ζ = 350/490 ≈ 0.71
    private static final double SPRING_STIFFNESS = 40.0;
    private static final double DAMPER_COEFFICIENT = 350.0;
    /** 压缩速度限幅：防止瞬移/生成时产生巨大阻尼力导致振荡。 */
    private static final double MAX_COMPRESSION_VEL = 0.3;
    private static final double MAX_STEERING_ANGLE = 35.0;
    private static final double STEERING_RATE = 3.5;
    private static final double SPEED_TO_YAW_FACTOR = 0.25;
    private static final double MIN_STEER_SPEED = 0.12;
    private static final double IDLE_RPM = 800;
    private static final double MAX_RPM = 7500;
    private static final double RPM_RATE = 120;
    private static final double BRAKE_FORCE = 0.06;
    private static final double NATURAL_DECEL = 0.995;
    private static final double FUEL_BASE_CONSUMPTION = 0.008;
    private static final double FUEL_ACCEL_FACTOR = 0.015;
    private static final int ABS_CYCLE = 4;
    private static final double ABS_SPEED_THRESHOLD = 0.3;
    private static final double ANTI_JITTER_SPEED = 0.03;

    // ── 自动换挡 RPM 阈值 ──
    private static final double UPSHIFT_RPM = 5200;
    private static final double DOWNSHIFT_RPM = 1800;

    // ═══════════════════════════════════════════════════════════════
    //  主入口
    // ═══════════════════════════════════════════════════════════════

    public static void applyPhysics(VehicleEntity vehicle, Level level) {
        // 燃料耗尽 → 仅自然减速
        if (vehicle.getFuel() <= 0 && Math.abs(vehicle.getSpeed()) < 0.001) {
            applyNaturalDeceleration(vehicle);
            applyMovement(vehicle);
            return;
        }

        advanceWheelStates(vehicle);
        updateSuspension(vehicle, level);
        updateEngineRPM(vehicle);
        autoGearShift(vehicle);
        applyDrivetrain(vehicle);
        applySteering(vehicle);

        // speed 修改器（先于移动，确保刹车即时生效）
        applyBrakingAndABS(vehicle);
        applyNaturalDeceleration(vehicle);

        // 主移动：speed → deltaMovement → move()
        applyMovement(vehicle);

        // 后处理（作用于移动后残余的 DM，下一 tick 被 applyMovement 覆盖）
        applyLateralFriction(vehicle);
        if (vehicle.isHandbrakeActive()) {
            applyHandbrakeDrift(vehicle);
        }
        applyAntiJitter(vehicle);
        consumeFuel(vehicle);
    }

    // ═══════════════════════════════════════════════════════════════
    //  0. 自动变速箱
    // ═══════════════════════════════════════════════════════════════

    /** 根据发动机 RPM 自动升降挡，同时处理倒挡/空挡切换。 */
    private static void autoGearShift(VehicleEntity vehicle) {
        int gear = vehicle.getGear();
        double speed = Math.abs(vehicle.getSpeed());

        // ── 倒挡逻辑：停止 + 刹车 → 进入倒挡 ──
        if (speed < 0.05 && vehicle.isBraking() && gear > -1) {
            vehicle.shiftGear(false); // 前进挡→空挡→倒挡（逐级降）
            return;
        }

        // ── 前进逻辑：倒挡/空挡 + 油门 → 回到 1 挡 ──
        if (gear <= 0 && vehicle.isAccelerating()) {
            vehicle.setGear(1); // 直接跳到 1 挡
            return;
        }

        // ── 空挡 / 倒挡：无自动换挡 ──
        if (gear <= 0) return;

        // ── 前进挡自动升降（基于 RPM）──
        double rpm = vehicle.getEngineRPM();

        // 升挡：高 RPM + 踩油门
        if (rpm > UPSHIFT_RPM && gear < 6 && vehicle.isAccelerating()) {
            vehicle.shiftGear(true);
        }

        // 降挡：低 RPM + 低速
        if (rpm < DOWNSHIFT_RPM && gear > 1 && speed < 0.3) {
            vehicle.shiftGear(false);
        }
    }

    // ═══════════════════════════════════════════════════════════════
    //  1. 悬挂系统
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

                double prevCompression = wheel.prevSuspensionLength;
                double compressionVel = compression - prevCompression;
                // 限幅：防止瞬移/生成时产生巨大阻尼力
                compressionVel = Math.max(-MAX_COMPRESSION_VEL,
                                          Math.min(compressionVel, MAX_COMPRESSION_VEL));
                double springForce = SPRING_STIFFNESS * compression;
                double damperForce = DAMPER_COEFFICIENT * compressionVel;
                double totalForce = springForce + damperForce;

                double mass = Math.max(1, vehicle.getVehicleWeight());
                double verticalImpulse = totalForce / mass;

                Vec3 currentDM = vehicle.getDeltaMovement();
                vehicle.setDeltaMovement(currentDM.x, currentDM.y + verticalImpulse, currentDM.z);

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
    //  2. 发动机 RPM
    // ═══════════════════════════════════════════════════════════════

    private static void updateEngineRPM(VehicleEntity vehicle) {
        double rpm = vehicle.getEngineRPM();
        double speed = Math.abs(vehicle.getSpeed());
        VehicleType type = vehicle.getVehicleTypeConfig();
        double wheelRadius = 0.4;
        int gear = vehicle.getGear();

        if (gear == 0) {
            // 空挡：油门加 RPM，松油门回怠速
            if (vehicle.isAccelerating()) {
                rpm = Math.min(rpm + RPM_RATE * 1.5, MAX_RPM);
            } else {
                rpm = moveToward(rpm, IDLE_RPM, RPM_RATE * 0.6);
            }
        } else {
            // 挂挡：RPM 与轮速耦合
            double gearRatio = type != null ? type.getGearRatio(gear) : 1.0;
            double finalDrive = type != null ? type.getEffectiveFinalDriveRatio() : 3.5;
            double wheelAngVel = speed / wheelRadius;
            double targetRPM = wheelAngVel * Math.abs(gearRatio) * finalDrive
                    * (60.0 / (2.0 * Math.PI));
            targetRPM = Math.max(IDLE_RPM * 0.6, Math.min(targetRPM, MAX_RPM));

            if (vehicle.isAccelerating()) {
                double throttleBoost = RPM_RATE * 2.0;
                rpm = moveToward(rpm, Math.min(targetRPM + throttleBoost, MAX_RPM), RPM_RATE * 2.5);
            } else {
                rpm = moveToward(rpm, targetRPM, RPM_RATE * 1.2);
            }
        }

        rpm = Math.max(IDLE_RPM * 0.5, Math.min(rpm, MAX_RPM));
        vehicle.setEngineRPM(rpm);
    }

    // ═══════════════════════════════════════════════════════════════
    //  3. 动力总成
    // ═══════════════════════════════════════════════════════════════

    private static void applyDrivetrain(VehicleEntity vehicle) {
        if (!vehicle.isAccelerating()) return;
        if (vehicle.getFuel() <= 0) return;

        int gear = vehicle.getGear();
        // 空挡无驱动力，但允许倒挡
        if (gear == 0) return;

        VehicleType type = vehicle.getVehicleTypeConfig();
        double gearRatio = type != null ? type.getGearRatio(gear) : 1.0;
        if (Math.abs(gearRatio) < 0.001) return;

        double finalDrive = type != null ? type.getEffectiveFinalDriveRatio() : 3.5;
        double efficiency = type != null ? type.transmissionEfficiency() : 0.9;
        double peakTorque = type != null ? type.enginePeakTorque() : 300.0;
        double peakRPM = type != null ? type.enginePeakRPM() : 4000.0;
        double wheelRadius = 0.4;

        double rpm = vehicle.getEngineRPM();
        double torque = torqueCurve(rpm, peakTorque, peakRPM);

        // 驱动力缩放
        double mcScale = 0.015;
        double driveForce = torque * Math.abs(gearRatio) * finalDrive * efficiency / wheelRadius * mcScale;

        // 倒挡反向
        if (gearRatio < 0 || gear == -1) {
            driveForce = -driveForce;
        }

        double mass = Math.max(1, vehicle.getVehicleWeight());
        double acceleration = driveForce / mass;

        // 将加速度添加到 speed 标量
        double newSpeed = vehicle.getSpeed() + acceleration;
        double maxSpeed = vehicle.getMaxSpeed();
        // 倒挡限速 30%
        if (newSpeed < 0) {
            newSpeed = Math.max(newSpeed, -maxSpeed * 0.3);
        } else {
            newSpeed = Math.min(newSpeed, maxSpeed);
        }
        vehicle.setSpeed(newSpeed);
    }

    private static double torqueCurve(double rpm, double peakTorque, double peakRPM) {
        if (rpm <= 0) return 0;
        if (rpm < peakRPM) {
            return peakTorque * (rpm / peakRPM);
        } else {
            return peakTorque * peakRPM / rpm;
        }
    }

    // ═══════════════════════════════════════════════════════════════
    //  4. 转向
    // ═══════════════════════════════════════════════════════════════

    private static void applySteering(VehicleEntity vehicle) {
        double targetAngle = 0;
        if (vehicle.isSteeringLeft())  targetAngle -= MAX_STEERING_ANGLE;
        if (vehicle.isSteeringRight()) targetAngle += MAX_STEERING_ANGLE;

        double currentAngle = vehicle.getSteeringAngle();
        if (currentAngle < targetAngle) {
            currentAngle = Math.min(currentAngle + STEERING_RATE, targetAngle);
        } else if (currentAngle > targetAngle) {
            currentAngle = Math.max(currentAngle - STEERING_RATE, targetAngle);
        } else {
            currentAngle = moveToward(currentAngle, 0, STEERING_RATE * 0.7);
        }
        vehicle.setSteeringAngle(currentAngle);

        // 更新车轮转向角
        Wheel[] wheels = vehicle.getWheels();
        if (wheels != null) {
            for (Wheel w : wheels) {
                w.steerAngle = w.isFront ? currentAngle : 0;
            }
        }

        // yaw 旋转
        double speed = Math.max(Math.abs(vehicle.getSpeed()), MIN_STEER_SPEED);
        double yawDelta = currentAngle * speed * SPEED_TO_YAW_FACTOR;
        vehicle.setYRot(vehicle.getYRot() + (float) yawDelta);
    }

    // ═══════════════════════════════════════════════════════════════
    //  5. 侧滑摩擦
    // ═══════════════════════════════════════════════════════════════

    private static void applyLateralFriction(VehicleEntity vehicle) {
        Wheel[] wheels = vehicle.getWheels();
        if (wheels == null) return;

        VehicleType type = vehicle.getVehicleTypeConfig();
        double corneringStiffness = type != null ? type.corneringStiffness() : 4.0;
        double mass = Math.max(1, vehicle.getVehicleWeight());

        for (Wheel wheel : wheels) {
            if (!wheel.grounded) continue;

            Vec3 wheelWorldVel = getWorldWheelVelocity(vehicle, wheel);
            if (wheelWorldVel.lengthSqr() < 0.0001) continue;

            float yawRad = (float) Math.toRadians(vehicle.getYRot());
            Vec3 forward = new Vec3(-Math.sin(yawRad), 0, Math.cos(yawRad));
            Vec3 right = new Vec3(Math.cos(yawRad), 0, Math.sin(yawRad));

            double longSpeed = wheelWorldVel.dot(forward);
            double latSpeed = wheelWorldVel.dot(right);
            double slipAngle = Math.atan2(Math.abs(latSpeed), Math.abs(longSpeed) + 0.001);

            double effectiveCornering = corneringStiffness;
            if (vehicle.isHandbrakeActive() && !wheel.isFront) {
                effectiveCornering *= 0.4;
            }

            double maxSlipAngle = 0.5;
            double clampedSlip = Math.min(slipAngle, maxSlipAngle);
            double maxLatForce = effectiveCornering * clampedSlip;
            double latForce = -Math.signum(latSpeed) * maxLatForce / mass;

            Vec3 currentDM = vehicle.getDeltaMovement();
            vehicle.setDeltaMovement(
                    currentDM.x + right.x * latForce,
                    currentDM.y,
                    currentDM.z + right.z * latForce);
        }
    }

    // ═══════════════════════════════════════════════════════════════
    //  6. 手刹漂移
    // ═══════════════════════════════════════════════════════════════

    private static void applyHandbrakeDrift(VehicleEntity vehicle) {
        Wheel[] wheels = vehicle.getWheels();
        if (wheels == null) return;

        double speed = Math.abs(vehicle.getSpeed());
        for (Wheel wheel : wheels) {
            if (wheel.isFront || !wheel.grounded) continue;
            if (speed > 0.1) {
                double handbrakeForce = BRAKE_FORCE * 1.3;
                double mass = Math.max(1, vehicle.getVehicleWeight());
                double decel = handbrakeForce * speed / mass;
                Vec3 horizDM = new Vec3(vehicle.getDeltaMovement().x, 0, vehicle.getDeltaMovement().z);
                double horizLen = horizDM.length();
                if (horizLen > 0.001) {
                    Vec3 brakeDir = horizDM.normalize().scale(-decel);
                    vehicle.setDeltaMovement(
                            vehicle.getDeltaMovement().x + brakeDir.x,
                            vehicle.getDeltaMovement().y,
                            vehicle.getDeltaMovement().z + brakeDir.z);
                }
            }
        }
    }

    // ═══════════════════════════════════════════════════════════════
    //  7. 制动 + ABS
    // ═══════════════════════════════════════════════════════════════

    private static void applyBrakingAndABS(VehicleEntity vehicle) {
        if (!vehicle.isBraking() && !vehicle.isHandbrakeActive()) return;

        double speed = Math.abs(vehicle.getSpeed());
        if (speed < 0.001) return;

        vehicle.setAbsTimer((vehicle.getAbsTimer() + 1) % ABS_CYCLE);

        boolean absActive = false;
        Wheel[] wheels = vehicle.getWheels();
        if (wheels != null) {
            for (Wheel wheel : wheels) {
                if (wheel.grounded && Math.abs(wheel.wheelAngularVelocity) < 0.01
                        && speed > ABS_SPEED_THRESHOLD) {
                    absActive = true;
                    break;
                }
            }
        }

        if (absActive && vehicle.getAbsTimer() >= ABS_CYCLE / 2) {
            return; // ABS 释放
        }

        double brakeDecel = BRAKE_FORCE;
        double newSpeed;
        if (vehicle.getSpeed() < 0) {
            newSpeed = Math.min(vehicle.getSpeed() + brakeDecel, 0);
        } else {
            newSpeed = Math.max(0, speed - brakeDecel);
        }
        vehicle.setSpeed(newSpeed);
    }

    // ═══════════════════════════════════════════════════════════════
    //  8. 自然减速
    // ═══════════════════════════════════════════════════════════════

    private static void applyNaturalDeceleration(VehicleEntity vehicle) {
        if (vehicle.isAccelerating() || vehicle.isBraking()) return;

        double speed = vehicle.getSpeed();
        if (Math.abs(speed) < 0.0005) {
            vehicle.setSpeed(0);
            return;
        }
        speed *= NATURAL_DECEL;
        if (Math.abs(speed) < 0.0005) speed = 0;
        vehicle.setSpeed(speed);
    }

    // ═══════════════════════════════════════════════════════════════
    //  9. 运动合成
    // ═══════════════════════════════════════════════════════════════

    private static void applyMovement(VehicleEntity vehicle) {
        double speed = vehicle.getSpeed();

        // 用 speed 标量构建水平运动方向（沿 yaw 方向）
        if (Math.abs(speed) > 0.0001) {
            float yaw = vehicle.getYRot();
            double rad = Math.toRadians(yaw);
            double dx = -Math.sin(rad) * speed;
            double dz =  Math.cos(rad) * speed;
            // 覆盖水平运动，保留垂直分量（悬挂/重力）
            vehicle.setDeltaMovement(dx, vehicle.getDeltaMovement().y, dz);
        } else {
            // 速度为零时清零水平运动
            vehicle.setDeltaMovement(0, vehicle.getDeltaMovement().y, 0);
        }

        vehicle.move(net.minecraft.world.entity.MoverType.SELF, vehicle.getDeltaMovement());

        // ★ 不再从 move() 后的 deltaMovement 回读同步 speed
        // MC 的 move() 会在实体着地时修改 deltaMovement（地面摩擦/碰撞），
        // 回读该值会造成 speed ↔ DM 反馈振荡 → 模型前后抽搐。
        // speed 完全由物理子系统控制：drivetrain / braking / naturalDecel
    }

    // ═══════════════════════════════════════════════════════════════
    //  10. 空中姿态控制（预留）
    // ═══════════════════════════════════════════════════════════════

    // ═══════════════════════════════════════════════════════════════
    //  11. 防抖锁定
    // ═══════════════════════════════════════════════════════════════

    private static void applyAntiJitter(VehicleEntity vehicle) {
        double speed = Math.abs(vehicle.getSpeed());
        boolean noInput = !vehicle.isAccelerating() && !vehicle.isBraking()
                && !vehicle.isHandbrakeActive();

        if (speed < ANTI_JITTER_SPEED && noInput) {
            vehicle.setDeltaMovement(0, vehicle.getDeltaMovement().y, 0);
            vehicle.setSpeed(0);
        }
    }

    // ═══════════════════════════════════════════════════════════════
    //  12. 燃油消耗
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
    //  辅助方法
    // ═══════════════════════════════════════════════════════════════

    private static void advanceWheelStates(VehicleEntity vehicle) {
        Wheel[] wheels = vehicle.getWheels();
        if (wheels == null) return;
        for (Wheel w : wheels) w.advanceState();
    }

    private static Vec3 getWorldWheelPos(VehicleEntity vehicle, Wheel wheel) {
        float yawRad = (float) Math.toRadians(vehicle.getYRot());
        double cos = Math.cos(yawRad);
        double sin = Math.sin(yawRad);
        double wx = wheel.localPos.x * cos - wheel.localPos.z * sin;
        double wz = wheel.localPos.x * sin + wheel.localPos.z * cos;
        return vehicle.position().add(wx, wheel.localPos.y, wz);
    }

    private static Vec3 getWorldWheelVelocity(VehicleEntity vehicle, Wheel wheel) {
        Vec3 vehicleVel = vehicle.getDeltaMovement();
        float yawRad = (float) Math.toRadians(vehicle.getYRot());
        double angularVel = vehicle.getSteeringAngle() * Math.abs(vehicle.getSpeed())
                * SPEED_TO_YAW_FACTOR * 0.1;
        double wx = wheel.localPos.x * Math.cos(yawRad) - wheel.localPos.z * Math.sin(yawRad);
        double wz = wheel.localPos.x * Math.sin(yawRad) + wheel.localPos.z * Math.cos(yawRad);
        double tangentVx = -wz * angularVel;
        double tangentVz =  wx * angularVel;
        return vehicleVel.add(tangentVx, 0, tangentVz);
    }

    private static double moveToward(double current, double target, double maxDelta) {
        if (current < target) return Math.min(current + maxDelta, target);
        if (current > target) return Math.max(current - maxDelta, target);
        return current;
    }
}
