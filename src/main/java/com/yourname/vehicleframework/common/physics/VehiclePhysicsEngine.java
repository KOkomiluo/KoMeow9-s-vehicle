package com.yourname.vehicleframework.common.physics;

import com.yourname.vehicleframework.common.entity.VehicleEntity;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;

/**
 * 车辆物理引擎。
 * <p>
 * 处理加速、刹车、摩擦力、转向和基础碰撞计算。
 * 每个 tick 由 VehicleEntity.tick() 在服务端调用。
 */
public final class VehiclePhysicsEngine {

    private VehiclePhysicsEngine() {}

    private static final double ACCELERATION_FACTOR = 0.015;
    private static final double BRAKE_FACTOR        = 0.04;
    private static final double NATURAL_DECEL       = 0.995;
    private static final double STEERING_SPEED      = 2.5;
    private static final double MAX_STEERING_ANGLE  = 30.0;
    private static final double SPEED_TO_YAW_FACTOR = 0.08;

    public static void applyPhysics(VehicleEntity vehicle, Level level) {
        if (vehicle.getFuel() <= 0) {
            applyNaturalDeceleration(vehicle);
            return;
        }
        applyAcceleration(vehicle);
        applySteering(vehicle);
        applyNaturalDeceleration(vehicle);
        applyTerrainFriction(vehicle, level);
        applyMovement(vehicle);
        consumeFuel(vehicle);
    }

    private static void applyAcceleration(VehicleEntity vehicle) {
        double maxSpeed = vehicle.getMaxSpeed();
        if (vehicle.isAccelerating()) {
            double newSpeed = vehicle.getSpeed() + ACCELERATION_FACTOR;
            vehicle.setSpeed(Math.min(newSpeed, maxSpeed));
        }
        if (vehicle.isBraking()) {
            if (vehicle.getSpeed() > 0) {
                vehicle.setSpeed(Math.max(vehicle.getSpeed() - BRAKE_FACTOR, 0));
            } else {
                vehicle.setSpeed(Math.max(vehicle.getSpeed() - ACCELERATION_FACTOR * 0.5, -maxSpeed * 0.3));
            }
        }
    }

    private static void applySteering(VehicleEntity vehicle) {
        double targetAngle = 0;
        if (vehicle.isSteeringLeft())  targetAngle -= MAX_STEERING_ANGLE;
        if (vehicle.isSteeringRight()) targetAngle += MAX_STEERING_ANGLE;

        double currentAngle = vehicle.getSteeringAngle();
        if (currentAngle < targetAngle) {
            currentAngle = Math.min(currentAngle + STEERING_SPEED, targetAngle);
        } else if (currentAngle > targetAngle) {
            currentAngle = Math.max(currentAngle - STEERING_SPEED, targetAngle);
        } else {
            currentAngle *= 0.85;
        }
        vehicle.setSteeringAngle(currentAngle);

        double speed = Math.abs(vehicle.getSpeed());
        if (speed > 0.01) {
            double yawDelta = currentAngle * speed * SPEED_TO_YAW_FACTOR;
            vehicle.setYRot(vehicle.getYRot() + (float) yawDelta);
        }
    }

    private static void applyNaturalDeceleration(VehicleEntity vehicle) {
        double speed = vehicle.getSpeed() * NATURAL_DECEL;
        if (Math.abs(speed) < 0.001) speed = 0;
        vehicle.setSpeed(speed);
    }

    private static void applyTerrainFriction(VehicleEntity vehicle, Level level) {
        // TODO: 根据地面方块类型调整摩擦系数（冰面/泥土等）
    }

    private static void applyMovement(VehicleEntity vehicle) {
        double speed = vehicle.getSpeed();
        if (Math.abs(speed) < 0.001) return;

        float yaw = vehicle.getYRot();
        double rad = Math.toRadians(yaw);
        double dx = -Math.sin(rad) * speed;
        double dz =  Math.cos(rad) * speed;

        vehicle.setDeltaMovement(dx, vehicle.getDeltaMovement().y, dz);
        vehicle.move(net.minecraft.world.entity.MoverType.SELF, vehicle.getDeltaMovement());
    }

    private static void consumeFuel(VehicleEntity vehicle) {
        double consumption = 0.01;
        if (vehicle.isAccelerating()) {
            consumption += 0.02 * Math.abs(vehicle.getSpeed());
        }
        vehicle.setFuel(vehicle.getFuel() - consumption);
    }

    public static double checkForwardCollision(VehicleEntity vehicle, Level level) {
        Vec3 frontPos = vehicle.position().add(
                -Math.sin(Math.toRadians(vehicle.getYRot())) * 2.0,
                0.5,
                 Math.cos(Math.toRadians(vehicle.getYRot())) * 2.0);
        BlockPos frontBlock = BlockPos.containing(frontPos);
        BlockState state = level.getBlockState(frontBlock);
        if (state.isSolid()) {
            return vehicle.getSpeed() > 0.5 ? 0.3 : 0.7;
        }
        return 1.0;
    }
}
