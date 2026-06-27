package com.yourname.vehicleframework.common.physics;

import com.yourname.vehicleframework.VehicleFramework;
import com.yourname.vehicleframework.common.entity.VehicleEntity;
import com.yourname.vehicleframework.data.VehicleType;

import net.minecraft.world.entity.MoverType;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

/**
 * Server-authoritative simcade vehicle physics.
 *
 * Horizontal velocity remains a real vector: steering changes yaw through a
 * bicycle model and tire forces change lateral velocity.  This is intentionally
 * less strict than a full rigid-body solver so keyboard driving remains stable
 * at Minecraft's 20 TPS.
 */
public final class VehiclePhysicsEngine {

    private VehiclePhysicsEngine() {}

    private static final double IDLE_RPM = 800.0;
    private static final double MAX_RPM = 7500.0;
    private static final double UPSHIFT_RPM = 5200.0;
    private static final double DOWNSHIFT_RPM = 1800.0;
    private static final double FUEL_BASE_CONSUMPTION = 0.008;
    private static final double FUEL_ACCEL_FACTOR = 0.015;
    private static final double FUEL_CONSUMPTION_SCALE = 1.0 / 6.0;
    private static final double LOW_SPEED_BLEND_START = 0.05;
    private static final double LOW_SPEED_BLEND_END = 0.24;
    private static final double REVERSE_STEER_SPEED_THRESHOLD = -0.01;
    private static final int TRACTION_LOG_INTERVAL_TICKS = 100;
    private static final float HALF_BLOCK_STEP_HEIGHT = 0.65f;
    private static final double TERRAIN_LAUNCH_SPEED_THRESHOLD = 0.55;
    private static final double TERRAIN_LAUNCH_MIN_STEP = 0.08;
    private static final double TERRAIN_LAUNCH_MAX_STEP = 0.55;
    private static final int TERRAIN_LAUNCH_COOLDOWN_TICKS = 8;
    private static final double[] FORWARD_GEAR_SPEED_FACTORS = {
            0.0, 0.30, 0.45, 0.60, 0.75, 0.88, 1.0
    };
    private static final double[] FORWARD_GEAR_DRIVE_FACTORS = {
            0.0, 1.0, 0.96, 0.92, 0.88, 0.84, 0.80
    };

    public static void applyPhysics(VehicleEntity vehicle, Level level) {
        VehicleType type = vehicle.getVehicleTypeConfig();
        if (type == null) type = VehicleType.DEFAULT;

        advanceWheelStates(vehicle);
        smoothInputs(vehicle, type);
        int groundedWheels = updateSuspension(vehicle, level, type);
        boolean bodyGroundFallback = groundedWheels == 0 && vehicle.onGround();
        int tractionWheels = bodyGroundFallback ? 4 : groundedWheels;
        double tractionFactor = clamp(tractionWheels / 4.0, 0.0, 1.0);
        logTractionState(vehicle, groundedWheels, tractionFactor, bodyGroundFallback);
        if (vehicle.isAutomaticShiftAvailable()) {
            autoGearShift(vehicle);
        }
        updateEngineRPM(vehicle, type);

        MotionResult result = integratePlanarMotion(vehicle, type, tractionWheels);
        updateBodyAttitude(vehicle, type, result);
        applyMovement(vehicle, tractionWheels);
        applyAntiJitter(vehicle);
        consumeFuel(vehicle);
    }

    private static void logTractionState(
            VehicleEntity vehicle, int groundedWheels,
            double tractionFactor, boolean bodyGroundFallback) {
        boolean hasDriveInput = vehicle.getThrottleInput() > 0.01
                || vehicle.getBrakeInput() > 0.01;
        if (!hasDriveInput
                || vehicle.tickCount % TRACTION_LOG_INTERVAL_TICKS != 0) {
            return;
        }

        VehicleFramework.LOGGER.info(
                "Vehicle traction: entity={}, throttle={}, gear={}, groundedWheels={}, "
                        + "tractionFactor={}, bodyGroundFallback={}",
                vehicle.getId(),
                String.format("%.2f", vehicle.getThrottleInput()),
                vehicle.getGear(),
                groundedWheels,
                String.format("%.2f", tractionFactor),
                bodyGroundFallback);
    }

    private static void smoothInputs(VehicleEntity vehicle, VehicleType type) {
        double response = type.getEffectiveInputResponse();
        vehicle.setThrottleInput(moveToward(
                vehicle.getThrottleInput(), vehicle.isAccelerating() ? 1.0 : 0.0, response));
        vehicle.setBrakeInput(moveToward(
                vehicle.getBrakeInput(), vehicle.isBraking() ? 1.0 : 0.0, response * 1.25));

        double steerTarget = 0.0;
        if (vehicle.isSteeringLeft()) steerTarget -= 1.0;
        if (vehicle.isSteeringRight()) steerTarget += 1.0;
        vehicle.setSteeringInput(moveToward(
                vehicle.getSteeringInput(), steerTarget, response));

        double speedSensitivity = 1.0 / (1.0 + Math.abs(vehicle.getSpeed()) * 1.35);
        double steerAngle = vehicle.getSteeringInput()
                * type.getEffectiveMaxSteeringAngle()
                * Math.max(0.38, speedSensitivity);
        vehicle.setSteeringAngle(steerAngle);

        double rearGripTarget = vehicle.isHandbrakeActive() ? 0.25 : 1.0;
        double rearGripRate = vehicle.isHandbrakeActive() ? 0.16 : 0.08;
        vehicle.setRearGripFactor(moveToward(
                vehicle.getRearGripFactor(), rearGripTarget, rearGripRate));

        Wheel[] wheels = vehicle.getWheels();
        if (wheels != null) {
            for (Wheel wheel : wheels) {
                wheel.steerAngle = wheel.isFront ? steerAngle : 0.0;
            }
        }
    }

    private static int updateSuspension(VehicleEntity vehicle, Level level, VehicleType type) {
        Wheel[] wheels = vehicle.getWheels();
        if (wheels == null) return 0;

        int grounded = 0;
        double verticalAcceleration = 0.0;
        double springScale = Math.max(0.2, type.springStiffness() / 50.0);
        double damperScale = Math.max(0.2, type.damperCoefficient() / 8.0);

        for (Wheel wheel : wheels) {
            Vec3 worldWheelPos = getWorldWheelPos(vehicle, wheel);
            Vec3 rayStart = worldWheelPos.add(0, Wheel.MAX_SUSPENSION_LENGTH * 0.5, 0);
            Vec3 rayEnd = worldWheelPos.subtract(
                    0, Wheel.MAX_SUSPENSION_LENGTH + wheel.radius, 0);

            BlockHitResult hit = level.clip(new ClipContext(
                    rayStart, rayEnd, ClipContext.Block.COLLIDER,
                    ClipContext.Fluid.NONE, vehicle));

            if (hit.getType() == HitResult.Type.BLOCK) {
                double groundDistance = worldWheelPos.y - hit.getLocation().y;
                double effectiveLength = groundDistance - wheel.radius;
                double compression = Wheel.REST_SUSPENSION_LENGTH - effectiveLength;
                compression = clamp(compression,
                        -Wheel.MAX_SUSPENSION_LENGTH * 0.3,
                        Wheel.REST_SUSPENSION_LENGTH - Wheel.MIN_SUSPENSION_LENGTH);

                double compressionVelocity = clamp(
                        compression - wheel.prevSuspensionLength, -0.3, 0.3);
                double wheelAcceleration =
                        compression * 0.06 * springScale
                        + compressionVelocity * 0.12 * damperScale;
                verticalAcceleration += clamp(wheelAcceleration, -0.03, 0.075);

                wheel.suspensionLength = compression;
                var face = hit.getDirection();
                wheel.setGrounded(true, hit.getLocation(),
                        new Vec3(face.getStepX(), face.getStepY(), face.getStepZ()));
                grounded++;
            } else {
                wheel.suspensionLength = -Wheel.REST_SUSPENSION_LENGTH * 0.3;
                wheel.setGrounded(false, Vec3.ZERO, new Vec3(0, 1, 0));
            }
        }

        Vec3 motion = vehicle.getDeltaMovement();
        vehicle.setDeltaMovement(motion.x,
                clamp(motion.y + verticalAcceleration, -0.9, 0.35), motion.z);
        return grounded;
    }

    private static MotionResult integratePlanarMotion(
            VehicleEntity vehicle, VehicleType type, int groundedWheels) {
        double yaw = Math.toRadians(vehicle.getYRot());
        Vec3 forward = new Vec3(-Math.sin(yaw), 0, Math.cos(yaw));
        Vec3 right = new Vec3(Math.cos(yaw), 0, Math.sin(yaw));
        Vec3 motion = vehicle.getDeltaMovement();

        double forwardSpeed = motion.dot(forward);
        double lateralSpeed = motion.dot(right);
        if (Math.abs(forwardSpeed) < 1.0e-4
                && Math.abs(lateralSpeed) < 1.0e-4
                && Math.abs(vehicle.getSpeed()) > 1.0e-4) {
            forwardSpeed = vehicle.getSpeed();
        }

        double groundFactor = clamp(groundedWheels / 4.0, 0.0, 1.0);
        double longitudinalAcceleration =
                calculateLongitudinalAcceleration(vehicle, type, forwardSpeed, groundFactor);

        double wheelBase = type.getEffectiveWheelBase();
        double frontWeight = clamp(type.weightDistribution(), 0.2, 0.8);
        double distanceToFront = wheelBase * (1.0 - frontWeight);
        double distanceToRear = wheelBase * frontWeight;
        double steering = Math.toRadians(vehicle.getSteeringAngle());
        double speedAbs = Math.abs(forwardSpeed);
        double safeSpeed = Math.max(speedAbs, 0.045);
        double yawRate = vehicle.getYawRate();
        boolean reversing = forwardSpeed < REVERSE_STEER_SPEED_THRESHOLD;
        double steeringForYaw = reversing ? -steering : steering;

        double frontSlip = Math.atan2(
                lateralSpeed + distanceToFront * yawRate, safeSpeed) - steering;
        double frontSlipForYaw = Math.atan2(
                lateralSpeed + distanceToFront * yawRate, safeSpeed) - steeringForYaw;
        double rearSlip = Math.atan2(
                lateralSpeed - distanceToRear * yawRate, safeSpeed);

        double tireStiffness = Math.max(1.0, type.tireLateralStiffness());
        double peakGrip = Math.max(0.2, type.tirePeakFriction());
        double maximumLateralAcceleration = 0.08 * peakGrip * groundFactor;
        double frontLateralAcceleration = clamp(
                -frontSlip * tireStiffness * 0.03 * frontWeight,
                -maximumLateralAcceleration * frontWeight,
                maximumLateralAcceleration * frontWeight);
        double frontYawLateralAcceleration = clamp(
                -frontSlipForYaw * tireStiffness * 0.03 * frontWeight,
                -maximumLateralAcceleration * frontWeight,
                maximumLateralAcceleration * frontWeight);
        double rearLateralAcceleration = clamp(
                -rearSlip * tireStiffness * 0.03 * (1.0 - frontWeight)
                        * vehicle.getRearGripFactor(),
                -maximumLateralAcceleration * (1.0 - frontWeight)
                        * vehicle.getRearGripFactor(),
                maximumLateralAcceleration * (1.0 - frontWeight)
                        * vehicle.getRearGripFactor());

        double lateralAcceleration = groundedWheels > 0
                ? frontLateralAcceleration + rearLateralAcceleration : 0.0;
        double yawAcceleration = groundedWheels > 0
                ? (distanceToFront * frontYawLateralAcceleration
                    - distanceToRear * rearLateralAcceleration)
                    / type.getEffectiveYawInertia()
                : 0.0;
        yawAcceleration = clamp(yawAcceleration, -0.04, 0.04);
        if (reversing) {
            yawAcceleration = -yawAcceleration;
        }

        double kinematicYawRate = groundedWheels > 0
                ? forwardSpeed / wheelBase * Math.tan(steeringForYaw) : 0.0;
        double dynamicYawRate = (yawRate + yawAcceleration) * 0.94;
        double dynamicBlend = smoothStep(LOW_SPEED_BLEND_START, LOW_SPEED_BLEND_END, speedAbs);
        yawRate = lerp(kinematicYawRate, dynamicYawRate, dynamicBlend);
        if (groundedWheels == 0) yawRate *= 0.96;
        yawRate = clamp(yawRate, -0.16, 0.16);
        vehicle.setYawRate(yawRate);

        double nextForwardSpeed = forwardSpeed + longitudinalAcceleration;
        double nextLateralSpeed = lateralSpeed + lateralAcceleration;
        if (speedAbs < 0.08 && !vehicle.isHandbrakeActive()) {
            nextLateralSpeed *= 0.62;
        }

        Vec3 horizontalMotion = forward.scale(nextForwardSpeed)
                .add(right.scale(nextLateralSpeed));
        double maxHorizontalSpeed = Math.max(0.1, vehicle.getMaxSpeed() * 1.08);
        if (horizontalMotion.length() > maxHorizontalSpeed) {
            horizontalMotion = horizontalMotion.normalize().scale(maxHorizontalSpeed);
        }

        vehicle.setDeltaMovement(
                horizontalMotion.x, motion.y, horizontalMotion.z);
        vehicle.setYRot(vehicle.getYRot() + (float) Math.toDegrees(yawRate));
        vehicle.setSpeed(nextForwardSpeed);
        updateWheelSlipState(vehicle, frontSlip, rearSlip,
                nextForwardSpeed, longitudinalAcceleration, lateralAcceleration);

        return new MotionResult(
                longitudinalAcceleration, lateralAcceleration, groundedWheels);
    }

    private static double calculateLongitudinalAcceleration(
            VehicleEntity vehicle, VehicleType type,
            double forwardSpeed, double groundFactor) {
        int gear = vehicle.getGear();
        double gearRatio = gear != 0 ? type.getGearRatio(gear) : 0.0;
        double direction = gearRatio < 0 || gear == -1 ? -1.0 : 1.0;
        double driveInput = gear == -1
                ? vehicle.getBrakeInput() : vehicle.getThrottleInput();
        double torqueFactor = clamp(
                torqueCurve(vehicle.getEngineRPM(),
                        type.enginePeakTorque(), type.enginePeakRPM())
                        / Math.max(1.0, type.enginePeakTorque()),
                0.72, 1.0);
        double gearFactor = getGearDriveFactor(gear);
        double gearSpeedLimit = getGearSpeedLimit(type, gear);
        double speedRatio = gearSpeedLimit > 0.0
                ? Math.abs(forwardSpeed) / gearSpeedLimit : 1.0;
        double topSpeedFactor = 1.0
                - smoothStep(0.97, 1.0, speedRatio);

        double acceleration = 0.0;
        if (vehicle.getFuel() > 0 && gear != 0) {
            acceleration += direction * type.acceleration()
                    * driveInput
                    * torqueFactor * gearFactor
                    * topSpeedFactor * groundFactor;
        }

        boolean brakingForward = forwardSpeed > 1.0e-4
                && vehicle.getBrakeInput() > 0.0;
        boolean brakingReverse = forwardSpeed < -1.0e-4
                && vehicle.getThrottleInput() > 0.0;
        if (brakingForward || brakingReverse) {
            double brakingInput = brakingForward
                    ? vehicle.getBrakeInput() : vehicle.getThrottleInput();
            acceleration -= Math.signum(forwardSpeed)
                    * type.brakingPower() * brakingInput;
        } else if (driveInput < 0.05 && Math.abs(forwardSpeed) > 1.0e-4) {
            double engineBrake = type.engineBrakingTorque() / 50000.0;
            acceleration -= Math.signum(forwardSpeed) * engineBrake;
        }

        if (Math.abs(forwardSpeed) > 1.0e-4) {
            double rolling = Math.max(0.0, type.rollingResistance());
            double drag = Math.max(0.0, type.aerodynamicDrag())
                    * forwardSpeed * Math.abs(forwardSpeed);
            acceleration -= Math.signum(forwardSpeed) * rolling;
            acceleration -= drag;
        }
        return clamp(acceleration, -0.09, 0.065);
    }

    private static void updateBodyAttitude(
            VehicleEntity vehicle, VehicleType type, MotionResult motion) {
        Wheel[] wheels = vehicle.getWheels();
        double terrainPitch = 0.0;
        double terrainRoll = 0.0;

        if (wheels != null) {
            AverageHeight front = averageGroundHeight(wheels, true, null);
            AverageHeight rear = averageGroundHeight(wheels, false, null);
            AverageHeight left = averageGroundHeight(wheels, null, true);
            AverageHeight right = averageGroundHeight(wheels, null, false);

            if (front.valid() && rear.valid()) {
                terrainPitch = -Math.toDegrees(Math.atan2(
                        front.height() - rear.height(), type.getEffectiveWheelBase()));
            }
            if (left.valid() && right.valid()) {
                terrainRoll = Math.toDegrees(Math.atan2(
                        left.height() - right.height(), type.getEffectiveTrackWidth()));
            }
        }

        double targetPitch = terrainPitch
                - motion.longitudinalAcceleration() * Math.max(0, type.bodyPitchStrength());
        double targetRoll = terrainRoll
                + motion.lateralAcceleration() * Math.max(0, type.bodyRollStrength());
        if (motion.groundedWheels() == 0) {
            targetPitch *= 0.35;
            targetRoll *= 0.35;
        }
        targetPitch = clamp(targetPitch, -12.0, 12.0);
        targetRoll = clamp(targetRoll, -15.0, 15.0);
        targetPitch = moveToward(vehicle.getBodyPitch(), targetPitch, 3.0);
        targetRoll = moveToward(vehicle.getBodyRoll(), targetRoll, 4.0);

        double pitchVelocity = (vehicle.getBodyPitchVelocity()
                + (targetPitch - vehicle.getBodyPitch()) * 0.20) * 0.68;
        double rollVelocity = (vehicle.getBodyRollVelocity()
                + (targetRoll - vehicle.getBodyRoll()) * 0.22) * 0.66;
        vehicle.setBodyPitchVelocity(pitchVelocity);
        vehicle.setBodyRollVelocity(rollVelocity);
        vehicle.setBodyPitch(vehicle.getBodyPitch() + pitchVelocity);
        vehicle.setBodyRoll(vehicle.getBodyRoll() + rollVelocity);
    }

    private static void applyMovement(VehicleEntity vehicle, int groundedWheels) {
        if (vehicle.getTerrainLaunchCooldown() > 0) {
            vehicle.setTerrainLaunchCooldown(vehicle.getTerrainLaunchCooldown() - 1);
        }

        Vec3 requestedMovement = vehicle.getDeltaMovement();
        double horizontalSpeed = Math.sqrt(
                requestedMovement.x * requestedMovement.x
                        + requestedMovement.z * requestedMovement.z);
        boolean allowHalfBlockStep = groundedWheels >= 2
                && Math.abs(vehicle.getBodyPitch()) < 11.0
                && requestedMovement.y < 0.18;
        vehicle.setMaxUpStep(allowHalfBlockStep ? HALF_BLOCK_STEP_HEIGHT : 0.25f);

        Vec3 before = vehicle.position();
        vehicle.move(MoverType.SELF, requestedMovement);
        Vec3 actualMovement = vehicle.position().subtract(before);
        double verticalMotion = actualMovement.y;

        boolean steppedUp = allowHalfBlockStep
                && actualMovement.y > TERRAIN_LAUNCH_MIN_STEP
                && actualMovement.y <= TERRAIN_LAUNCH_MAX_STEP;
        if (steppedUp
                && horizontalSpeed > TERRAIN_LAUNCH_SPEED_THRESHOLD
                && vehicle.getTerrainLaunchCooldown() <= 0) {
            double launch = 0.10
                    + (horizontalSpeed - TERRAIN_LAUNCH_SPEED_THRESHOLD) * 0.05
                    + Math.min(actualMovement.y, 0.5) * 0.06;
            verticalMotion = clamp(launch, 0.10, 0.18);
            vehicle.setTerrainLaunchCooldown(TERRAIN_LAUNCH_COOLDOWN_TICKS);
        } else if (steppedUp) {
            verticalMotion = Math.min(requestedMovement.y, 0.03);
        }

        if (vehicle.horizontalCollision) {
            vehicle.setYawRate(vehicle.getYawRate() * 0.45);
            vehicle.setSpeed(vehicle.getSpeed() * 0.55);
        }
        vehicle.setDeltaMovement(actualMovement.x, verticalMotion, actualMovement.z);
    }

    private static void updateEngineRPM(VehicleEntity vehicle, VehicleType type) {
        double rpm = vehicle.getEngineRPM();
        double targetRpm = calculateArcadeRpm(
                type, vehicle.getSpeed(), vehicle.getGear(),
                vehicle.getThrottleInput());
        double inertia = Math.max(0.05, type.engineInertia());
        rpm += (targetRpm - rpm) * clamp(0.16 / inertia, 0.08, 0.42);
        vehicle.setEngineRPM(clamp(rpm, IDLE_RPM * 0.75, MAX_RPM));
    }

    public static double calculateArcadeRpm(
            VehicleType type, double speed, int gear, double throttleInput) {
        if (gear == 0) {
            return IDLE_RPM + clamp(throttleInput, 0.0, 1.0) * 4200.0;
        }

        double gearSpeedLimit = getGearSpeedLimit(type, gear);
        if (gearSpeedLimit <= 0.0) return IDLE_RPM;

        double normalizedSpeed = clamp(
                Math.abs(speed) / gearSpeedLimit, 0.0, 1.0);
        return IDLE_RPM + normalizedSpeed * (MAX_RPM - IDLE_RPM);
    }

    public static double getGearSpeedLimit(VehicleType type, int gear) {
        if (type == null) type = VehicleType.DEFAULT;
        if (gear == -1) return type.maxSpeed() * 0.30;
        if (gear <= 0) return 0.0;
        int index = Math.min(gear, FORWARD_GEAR_SPEED_FACTORS.length - 1);
        return type.maxSpeed() * FORWARD_GEAR_SPEED_FACTORS[index];
    }

    private static double getGearDriveFactor(int gear) {
        if (gear == -1) return 0.90;
        if (gear <= 0) return 0.0;
        int index = Math.min(gear, FORWARD_GEAR_DRIVE_FACTORS.length - 1);
        return FORWARD_GEAR_DRIVE_FACTORS[index];
    }

    private static void autoGearShift(VehicleEntity vehicle) {
        int gear = vehicle.getGear();
        double speed = Math.abs(vehicle.getSpeed());

        if (speed < 0.035 && vehicle.isBraking()) {
            vehicle.setGear(-1);
            return;
        }
        if (speed < 0.035 && vehicle.isAccelerating()) {
            vehicle.setGear(1);
            return;
        }
        if (gear <= 0) return;

        double rpm = vehicle.getEngineRPM();
        if (rpm > UPSHIFT_RPM && gear < 6 && vehicle.isAccelerating()) {
            vehicle.shiftGear(true);
        } else if (rpm < DOWNSHIFT_RPM && gear > 1 && speed < 0.45) {
            vehicle.shiftGear(false);
        }
    }

    private static void applyAntiJitter(VehicleEntity vehicle) {
        Vec3 motion = vehicle.getDeltaMovement();
        if (Math.abs(vehicle.getSpeed()) < 0.008
                && vehicle.getThrottleInput() < 0.02
                && vehicle.getBrakeInput() < 0.02
                && !vehicle.isHandbrakeActive()) {
            vehicle.setDeltaMovement(0, motion.y, 0);
            vehicle.setSpeed(0);
            vehicle.setYawRate(vehicle.getYawRate() * 0.5);
        }
    }

    private static void consumeFuel(VehicleEntity vehicle) {
        double consumption = FUEL_BASE_CONSUMPTION;
        double driveInput = vehicle.getGear() == -1
                ? vehicle.getBrakeInput() : vehicle.getThrottleInput();
        if (driveInput > 0.01) {
            double rpmFactor = vehicle.getEngineRPM() / IDLE_RPM;
            consumption += FUEL_ACCEL_FACTOR
                    * driveInput
                    * Math.max(0.2, Math.abs(vehicle.getSpeed()))
                    * rpmFactor;
        }
        vehicle.setFuel(vehicle.getFuel() - consumption * FUEL_CONSUMPTION_SCALE);
    }

    private static void updateWheelSlipState(
            VehicleEntity vehicle, double frontSlip, double rearSlip,
            double forwardSpeed, double longitudinalAcceleration,
            double lateralAcceleration) {
        Wheel[] wheels = vehicle.getWheels();
        if (wheels == null) return;
        for (Wheel wheel : wheels) {
            wheel.slipAngle = wheel.isFront ? frontSlip : rearSlip;
            wheel.slipRatio = clamp(
                    longitudinalAcceleration / Math.max(0.02, Math.abs(forwardSpeed)),
                    -1.0, 1.0);
            wheel.longitudinalForce = longitudinalAcceleration
                    * vehicle.getVehicleWeight() / Math.max(1, wheels.length);
            wheel.lateralForce = lateralAcceleration
                    * vehicle.getVehicleWeight() / Math.max(1, wheels.length);
            wheel.wheelRotationSpeed = forwardSpeed / Math.max(0.05, wheel.radius);
            wheel.wheelAngularVelocity = wheel.wheelRotationSpeed;
        }
    }

    private static AverageHeight averageGroundHeight(
            Wheel[] wheels, Boolean front, Boolean left) {
        double total = 0.0;
        int count = 0;
        for (Wheel wheel : wheels) {
            if (!wheel.grounded) continue;
            if (front != null && wheel.isFront != front) continue;
            if (left != null && wheel.isLeft != left) continue;
            total += wheel.groundPoint.y;
            count++;
        }
        return count == 0
                ? new AverageHeight(0.0, false)
                : new AverageHeight(total / count, true);
    }

    private static void advanceWheelStates(VehicleEntity vehicle) {
        Wheel[] wheels = vehicle.getWheels();
        if (wheels == null) return;
        for (Wheel wheel : wheels) wheel.advanceState();
    }

    private static Vec3 getWorldWheelPos(VehicleEntity vehicle, Wheel wheel) {
        double yaw = Math.toRadians(vehicle.getYRot());
        double cos = Math.cos(yaw);
        double sin = Math.sin(yaw);
        double worldX = wheel.localPos.x * cos - wheel.localPos.z * sin;
        double worldZ = wheel.localPos.x * sin + wheel.localPos.z * cos;
        return vehicle.position().add(worldX, wheel.localPos.y, worldZ);
    }

    private static double torqueCurve(double rpm, double peakTorque, double peakRpm) {
        if (rpm <= 0 || peakTorque <= 0 || peakRpm <= 0) return 0;
        if (rpm < peakRpm) return peakTorque * (rpm / peakRpm);
        return peakTorque * peakRpm / rpm;
    }

    private static double moveToward(double current, double target, double maxDelta) {
        if (current < target) return Math.min(current + maxDelta, target);
        if (current > target) return Math.max(current - maxDelta, target);
        return current;
    }

    private static double smoothStep(double edge0, double edge1, double value) {
        double x = clamp((value - edge0) / (edge1 - edge0), 0.0, 1.0);
        return x * x * (3.0 - 2.0 * x);
    }

    private static double lerp(double from, double to, double amount) {
        return from + (to - from) * amount;
    }

    private static double clamp(double value, double min, double max) {
        return Math.max(min, Math.min(value, max));
    }

    private record MotionResult(
            double longitudinalAcceleration,
            double lateralAcceleration,
            int groundedWheels) {}

    private record AverageHeight(double height, boolean valid) {}
}
