package com.yourname.vehicleframework.common.physics;

import com.yourname.vehicleframework.common.entity.VehicleEntity;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.VoxelShape;

import java.util.List;

/**
 * 碰撞计算器。
 * <p>
 * 处理车辆与世界的碰撞检测、碰撞响应，以及车辆与其他实体的碰撞交互。
 */
public final class CollisionCalculator {

    private CollisionCalculator() {}

    public static Vec3 calculateCollisionNormal(VehicleEntity vehicle, Level level) {
        AABB box = vehicle.getBoundingBox();
        if (box == null) return null;

        AABB expanded = box.inflate(0.1);
        BlockPos min = BlockPos.containing(expanded.minX, expanded.minY, expanded.minZ);
        BlockPos max = BlockPos.containing(expanded.maxX, expanded.maxY, expanded.maxZ);

        Vec3 normal = Vec3.ZERO;
        boolean collided = false;

        for (BlockPos pos : BlockPos.betweenClosed(min, max)) {
            BlockState state = level.getBlockState(pos);
            if (state.isAir()) continue;

            VoxelShape shape = state.getCollisionShape(level, pos);
            if (shape.isEmpty()) continue;

            AABB blockBox = shape.bounds().move(pos);
            if (expanded.intersects(blockBox)) {
                collided = true;
                Vec3 center = blockBox.getCenter();
                Vec3 vehicleCenter = box.getCenter();
                normal = normal.add(vehicleCenter.subtract(center).normalize());
            }
        }
        return collided ? normal.normalize() : null;
    }

    public static void applyCollisionResponse(VehicleEntity vehicle, Vec3 normal, double bounciness) {
        Vec3 velocity = vehicle.getDeltaMovement();
        double dot = velocity.dot(normal);
        Vec3 reflected = velocity.subtract(normal.scale(2.0 * dot));
        Vec3 response = velocity.scale(1.0 - bounciness).add(reflected.scale(bounciness));
        vehicle.setDeltaMovement(response.scale(0.5));
        vehicle.setSpeed(vehicle.getSpeed() * 0.5);
    }

    public static List<net.minecraft.world.entity.Entity> getEntitiesInFront(
            VehicleEntity vehicle, Level level, double range, double angle) {
        float yaw = vehicle.getYRot();
        double rad = Math.toRadians(yaw);
        Vec3 frontDir = new Vec3(-Math.sin(rad), 0, Math.cos(rad));
        Vec3 frontPos = vehicle.position().add(frontDir.scale(range * 0.5));

        AABB searchBox = new AABB(
                frontPos.x - range, frontPos.y - 1, frontPos.z - range,
                frontPos.x + range, frontPos.y + 2, frontPos.z + range);

        return level.getEntities(vehicle, searchBox, entity -> {
            if (entity == vehicle) return false;
            Vec3 toEntity = entity.position().subtract(vehicle.position()).normalize();
            return frontDir.dot(toEntity) > Math.cos(Math.toRadians(angle));
        });
    }
}
