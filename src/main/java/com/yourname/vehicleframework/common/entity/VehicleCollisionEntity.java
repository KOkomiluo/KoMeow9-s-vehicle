package com.yourname.vehicleframework.common.entity;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

public class VehicleCollisionEntity extends Entity {

    private VehicleEntity parentVehicle;
    private Vec3 relativeOffset = Vec3.ZERO;

    public VehicleCollisionEntity(EntityType<?> entityType, Level level) {
        super(entityType, level);
        this.blocksBuilding = true;
    }

    @Override
    protected void defineSynchedData() {
        // 无需同步额外数据
    }

    @Override
    public void tick() {
        super.tick();
        if (parentVehicle != null && parentVehicle.isAlive()) {
            Vec3 worldOffset = relativeOffset.yRot((float) Math.toRadians(-parentVehicle.getYRot()));
            setPos(parentVehicle.getX() + worldOffset.x, parentVehicle.getY() + worldOffset.y, parentVehicle.getZ() + worldOffset.z);
            setYRot(parentVehicle.getYRot());
        } else if (!level().isClientSide && parentVehicle != null && !parentVehicle.isAlive()) {
            discard();
        }
    }

    @Override public boolean canBeCollidedWith() { return true; }
    @Override public boolean isPickable() { return true; }
    @Override protected void readAdditionalSaveData(CompoundTag tag) {}
    @Override protected void addAdditionalSaveData(CompoundTag tag) {}
    public VehicleEntity getParentVehicle() { return parentVehicle; }
}
