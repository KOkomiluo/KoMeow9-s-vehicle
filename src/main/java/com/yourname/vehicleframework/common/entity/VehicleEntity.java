package com.yourname.vehicleframework.common.entity;

import com.yourname.vehicleframework.api.IVehicleDriveable;
import com.yourname.vehicleframework.common.item.VehicleDismantleItem;
import com.yourname.vehicleframework.common.physics.VehiclePhysicsEngine;
import com.yourname.vehicleframework.common.registry.ModItemRegistry;
import com.yourname.vehicleframework.data.VehicleType;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;

import software.bernie.geckolib.animatable.GeoEntity;
import software.bernie.geckolib.core.animatable.instance.AnimatableInstanceCache;
import software.bernie.geckolib.core.animation.AnimatableManager;
import software.bernie.geckolib.core.animation.AnimationController;
import software.bernie.geckolib.core.animation.AnimationState;
import software.bernie.geckolib.core.object.PlayState;
import software.bernie.geckolib.core.animation.RawAnimation;
import software.bernie.geckolib.util.GeckoLibUtil;

public class VehicleEntity extends Entity implements GeoEntity, IVehicleDriveable {

    private static final EntityDataAccessor<Float> DATA_SPEED =
            SynchedEntityData.defineId(VehicleEntity.class, EntityDataSerializers.FLOAT);
    private static final EntityDataAccessor<Float> DATA_FUEL =
            SynchedEntityData.defineId(VehicleEntity.class, EntityDataSerializers.FLOAT);
    private static final EntityDataAccessor<Integer> DATA_GEAR =
            SynchedEntityData.defineId(VehicleEntity.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<String> DATA_VEHICLE_TYPE =
            SynchedEntityData.defineId(VehicleEntity.class, EntityDataSerializers.STRING);

    private final AnimatableInstanceCache animCache = GeckoLibUtil.createInstanceCache(this);
    private static final RawAnimation DRIVE_ANIM = RawAnimation.begin().thenLoop("animation.vehicle.drive");
    private static final RawAnimation IDLE_ANIM  = RawAnimation.begin().thenLoop("animation.vehicle.idle");

    private VehicleType vehicleType;
    private double speed, maxSpeed = 1.0, steeringAngle;
    private double fuel = 100.0, maxFuel = 100.0;
    private int gear = 0;
    private boolean accelerating, braking, steeringLeft, steeringRight;

    public VehicleEntity(EntityType<?> entityType, Level level) {
        super(entityType, level);
        this.blocksBuilding = true;
    }

    @Override
    protected void defineSynchedData() {
        this.entityData.define(DATA_SPEED, 0.0f);
        this.entityData.define(DATA_FUEL, 100.0f);
        this.entityData.define(DATA_GEAR, 0);
        this.entityData.define(DATA_VEHICLE_TYPE, "sports_car");
    }

    @Override
    protected void readAdditionalSaveData(CompoundTag tag) {
        this.speed = tag.getDouble("Speed");
        this.fuel = tag.getDouble("Fuel");
        this.gear = tag.getInt("Gear");
        this.steeringAngle = tag.getDouble("SteeringAngle");
        String typeKey = tag.getString("VehicleType");
        if (!typeKey.isEmpty()) entityData.set(DATA_VEHICLE_TYPE, typeKey);
    }

    @Override
    protected void addAdditionalSaveData(CompoundTag tag) {
        tag.putDouble("Speed", speed);
        tag.putDouble("Fuel", fuel);
        tag.putInt("Gear", gear);
        tag.putDouble("SteeringAngle", steeringAngle);
        tag.putString("VehicleType", entityData.get(DATA_VEHICLE_TYPE));
    }

    @Override
    public void tick() {
        super.tick();
        if (!level().isClientSide) {
            if (isBeingDriven()) VehiclePhysicsEngine.applyPhysics(this, level());
            else { speed *= 0.95; if (Math.abs(speed) < 0.001) speed = 0; }
            entityData.set(DATA_SPEED, (float) speed);
            entityData.set(DATA_FUEL, (float) fuel);
            entityData.set(DATA_GEAR, gear);
        }
    }

    public void handleDrivingInput(boolean accel, boolean brake, boolean left, boolean right) {
        this.accelerating = accel; this.braking = brake;
        this.steeringLeft = left; this.steeringRight = right;
    }

    @Override public double  getSpeed()       { return speed; }
    @Override public double  getMaxSpeed()    { return maxSpeed; }
    @Override public double  getFuel()        { return fuel; }
    @Override public double  getMaxFuel()     { return maxFuel; }
    @Override public int     getGear()        { return gear; }
    @Override public boolean isBeingDriven()  { return getControllingPassenger() instanceof Player; }
    @Override public void setAccelerating(boolean v) { this.accelerating = v; }
    @Override public void setBraking(boolean v)      { this.braking = v; }
    @Override public void setFuel(double f)           { this.fuel = Math.max(0, Math.min(f, maxFuel)); }
    @Override public void setSteering(boolean left, boolean right) { this.steeringLeft = left; this.steeringRight = right; }

    @Override
    public Player getDriver() {
        Entity c = getControllingPassenger();
        return c instanceof Player ? (Player) c : null;
    }

    @Override
    public void shiftGear(boolean up) {
        if (up && gear < 6) gear++;
        else if (!up && gear > -1) gear--;
    }

    public boolean isAccelerating()  { return accelerating; }
    public boolean isBraking()       { return braking; }
    public boolean isSteeringLeft()  { return steeringLeft; }
    public boolean isSteeringRight() { return steeringRight; }
    public double  getSteeringAngle(){ return steeringAngle; }

    public void setSpeed(double s) { this.speed = Math.max(-maxSpeed * 0.3, Math.min(s, maxSpeed)); }
    public void setSteeringAngle(double a) { this.steeringAngle = Math.max(-30.0, Math.min(a, 30.0)); }

    public void setVehicleType(VehicleType type) {
        this.vehicleType = type;
        if (type != null) { maxSpeed = type.maxSpeed(); maxFuel = type.fuelCapacity(); fuel = maxFuel; }
    }

    public VehicleType getVehicleTypeConfig() { return vehicleType; }

    /** 获取当前车辆配置的 key（用于 spawn item NBT）。 */
    public String getVehicleTypeKey() {
        String key = entityData.get(DATA_VEHICLE_TYPE);
        return key != null && !key.isEmpty() ? key : "sports_car";
    }

    @Override protected boolean canRide(Entity e) { return e instanceof Player; }

    @Override
    public InteractionResult interact(Player player, InteractionHand hand) {
        ItemStack heldItem = player.getItemInHand(hand);

        if (!level().isClientSide) {
            // ── 拆卸扳手：将载具变回生成器掉落物 ──
            if (heldItem.getItem() instanceof VehicleDismantleItem) {
                // 先驱逐所有乘客
                ejectPassengers();
                // 生成带有车型 NBT 的掉落物
                ItemStack spawnStack = ModItemRegistry.createVehicleSpawnStack(getVehicleTypeKey());
                this.spawnAtLocation(spawnStack);
                // 移除载具实体
                this.discard();
                // 消耗扳手耐久
                heldItem.hurtAndBreak(1, player, (p) -> {});
                return InteractionResult.SUCCESS;
            }

            // ── 驾驶：右键骑乘 ──
            if (getControllingPassenger() == null) {
                player.startRiding(this);
            } else if (!getPassengers().contains(player)) {
                player.startRiding(this);
            }
        }
        return InteractionResult.SUCCESS;
    }

    @Override
    public boolean canBeCollidedWith()  { return true; }
    @Override public boolean isPickable()          { return true; }

    @Override
    protected boolean canAddPassenger(Entity passenger) {
        return passenger instanceof Player && getPassengers().isEmpty();
    }

    @Override
    public void registerControllers(AnimatableManager.ControllerRegistrar controllers) {
        controllers.add(new AnimationController<>(this, "drive_controller", 2, this::drivePredicate));
    }

    private PlayState drivePredicate(AnimationState<VehicleEntity> state) {
        state.setAnimation(Math.abs(speed) > 0.01 ? DRIVE_ANIM : IDLE_ANIM);
        return PlayState.CONTINUE;
    }

    @Override
    public AnimatableInstanceCache getAnimatableInstanceCache() { return animCache; }
}
