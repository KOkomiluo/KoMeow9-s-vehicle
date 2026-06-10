package com.yourname.vehicleframework.common.entity;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

public class VehicleSeatEntity extends Entity {

    private static final EntityDataAccessor<Integer> DATA_SEAT_INDEX =
            SynchedEntityData.defineId(VehicleSeatEntity.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Boolean> DATA_IS_DRIVER =
            SynchedEntityData.defineId(VehicleSeatEntity.class, EntityDataSerializers.BOOLEAN);

    private int seatIndex;
    private boolean isDriverSeat;
    private VehicleEntity parentVehicle;

    public VehicleSeatEntity(EntityType<?> entityType, Level level) {
        super(entityType, level);
        this.noPhysics = true;
        this.setInvisible(true);
    }

    @Override
    protected void defineSynchedData() {
        this.entityData.define(DATA_SEAT_INDEX, 0);
        this.entityData.define(DATA_IS_DRIVER, false);
    }

    @Override
    protected void readAdditionalSaveData(CompoundTag tag) {
        this.seatIndex = tag.getInt("SeatIndex");
        this.isDriverSeat = tag.getBoolean("IsDriverSeat");
    }

    @Override
    protected void addAdditionalSaveData(CompoundTag tag) {
        tag.putInt("SeatIndex", seatIndex);
        tag.putBoolean("IsDriverSeat", isDriverSeat);
    }

    @Override
    public void tick() {
        super.tick();
        if (parentVehicle != null && parentVehicle.isAlive()) {
            Vec3 offset = getSeatOffset();
            setPos(parentVehicle.getX() + offset.x, parentVehicle.getY() + offset.y, parentVehicle.getZ() + offset.z);
            setYRot(parentVehicle.getYRot());
        } else if (!level().isClientSide && parentVehicle != null && !parentVehicle.isAlive()) {
            discard();
        }
    }

    private Vec3 getSeatOffset() {
        if (isDriverSeat) return new Vec3(-0.4, 0.6, 0.0);
        return new Vec3(0.4 * (seatIndex % 2 == 0 ? 1 : -1), 0.6, -0.5 * (seatIndex / 2));
    }

    @Override protected boolean canAddPassenger(Entity p) { return getPassengers().isEmpty() && p instanceof Player; }
    public boolean isDriverSeat() { return isDriverSeat; }
    public int getSeatIndex() { return seatIndex; }
    public VehicleEntity getParentVehicle() { return parentVehicle; }
    @Override public boolean isPickable() { return !isRemoved(); }
    @Override public boolean canBeCollidedWith() { return false; }
}
