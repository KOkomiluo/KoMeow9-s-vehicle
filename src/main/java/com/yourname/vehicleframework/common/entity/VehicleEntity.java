package com.yourname.vehicleframework.common.entity;

import com.yourname.vehicleframework.api.IVehicleDriveable;
import com.yourname.vehicleframework.common.item.VehicleDismantleItem;
import com.yourname.vehicleframework.common.physics.VehiclePhysicsEngine;
import com.yourname.vehicleframework.common.physics.Wheel;
import com.yourname.vehicleframework.common.registry.ModItemRegistry;
import com.yourname.vehicleframework.data.SeatConfig;
import com.yourname.vehicleframework.data.VehicleType;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityDimensions;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MoverType;
import net.minecraft.world.entity.Pose;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

public class VehicleEntity extends Entity implements IVehicleDriveable {

    private static final EntityDataAccessor<Float> DATA_SPEED =
            SynchedEntityData.defineId(VehicleEntity.class, EntityDataSerializers.FLOAT);
    private static final EntityDataAccessor<Float> DATA_FUEL =
            SynchedEntityData.defineId(VehicleEntity.class, EntityDataSerializers.FLOAT);
    private static final EntityDataAccessor<Integer> DATA_GEAR =
            SynchedEntityData.defineId(VehicleEntity.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<String> DATA_VEHICLE_TYPE =
            SynchedEntityData.defineId(VehicleEntity.class, EntityDataSerializers.STRING);
    private static final EntityDataAccessor<Float> DATA_ENGINE_RPM =
            SynchedEntityData.defineId(VehicleEntity.class, EntityDataSerializers.FLOAT);
    private static final EntityDataAccessor<String> DATA_OBJ_PATH =
            SynchedEntityData.defineId(VehicleEntity.class, EntityDataSerializers.STRING);
    private static final EntityDataAccessor<Float> DATA_OBJ_SCALE =
            SynchedEntityData.defineId(VehicleEntity.class, EntityDataSerializers.FLOAT);
    private static final EntityDataAccessor<Float> DATA_BODY_PITCH =
            SynchedEntityData.defineId(VehicleEntity.class, EntityDataSerializers.FLOAT);
    private static final EntityDataAccessor<Float> DATA_BODY_ROLL =
            SynchedEntityData.defineId(VehicleEntity.class, EntityDataSerializers.FLOAT);
    private static final EntityDataAccessor<Float> DATA_STEERING_ANGLE =
            SynchedEntityData.defineId(VehicleEntity.class, EntityDataSerializers.FLOAT);
    private static final EntityDataAccessor<Float> DATA_DRIVER_SEAT_X =
            SynchedEntityData.defineId(VehicleEntity.class, EntityDataSerializers.FLOAT);
    private static final EntityDataAccessor<Float> DATA_DRIVER_SEAT_Y =
            SynchedEntityData.defineId(VehicleEntity.class, EntityDataSerializers.FLOAT);
    private static final EntityDataAccessor<Float> DATA_DRIVER_SEAT_Z =
            SynchedEntityData.defineId(VehicleEntity.class, EntityDataSerializers.FLOAT);

    private VehicleType vehicleType;
    private double speed, maxSpeed = 1.0, steeringAngle;
    private double fuel = 100.0, maxFuel = 100.0;
    private int gear = 1; // 默认 1 挡（非空挡）
    private boolean accelerating, braking, steeringLeft, steeringRight, handbrake;

    // ── 新增物理状态 ──
    private Wheel[] wheels;
    private double engineRPM;
    private int absTimer;
    private boolean wheelsInitialized;
    private double yawRate;
    private double throttleInput, brakeInput, steeringInput;
    private double rearGripFactor = 1.0;
    private double bodyPitch, bodyRoll;
    private double bodyPitchVelocity, bodyRollVelocity;
    private float clientBodyPitch, clientBodyRoll;
    private float clientBodyPitchOld, clientBodyRollOld;
    private float clientVisualYaw, clientVisualYawOld, clientVisualYawTarget;
    private float clientVisualYawVelocity;
    private boolean clientVisualYawInitialized;

    // ── 客户端 60fps 插值暂存（baseTick 之后应用）──
    /** 暂存的服务端目标位置 — baseTick() 运行后再应用到 setPos */
    private double clientTargetX, clientTargetY, clientTargetZ;
    private float clientTargetXRot;
    private boolean clientVisualYawResetPending;
    private boolean hasClientTarget;

    /** 由 VehicleCameraHandler 在 Phase.END 调用 */
    public void applyClientTarget() {
        if (!hasClientTarget) return;
        this.setPos(clientTargetX, clientTargetY, clientTargetZ);
        this.setXRot(clientTargetXRot);
        this.hasClientTarget = false;
    }

    // 默认车重（在 VehicleType 可用前作为后备）
    private double vehicleWeight = 1500.0;

    public VehicleEntity(EntityType<?> entityType, Level level) {
        super(entityType, level);
        this.blocksBuilding = true;
        this.fuel = this.maxFuel;
        this.engineRPM = 800; // 怠速
    }

    @Override
    protected void defineSynchedData() {
        this.entityData.define(DATA_SPEED, 0.0f);
        this.entityData.define(DATA_FUEL, 100.0f);
        this.entityData.define(DATA_GEAR, 1);
        this.entityData.define(DATA_VEHICLE_TYPE, "sports_car");
        this.entityData.define(DATA_ENGINE_RPM, 800.0f);
        this.entityData.define(DATA_OBJ_PATH, "");
        this.entityData.define(DATA_OBJ_SCALE, 0.0625f);
        this.entityData.define(DATA_BODY_PITCH, 0.0f);
        this.entityData.define(DATA_BODY_ROLL, 0.0f);
        this.entityData.define(DATA_STEERING_ANGLE, 0.0f);
        this.entityData.define(DATA_DRIVER_SEAT_X, (float) SeatConfig.DEFAULT_DRIVER.x());
        this.entityData.define(DATA_DRIVER_SEAT_Y, (float) SeatConfig.DEFAULT_DRIVER.y());
        this.entityData.define(DATA_DRIVER_SEAT_Z, (float) SeatConfig.DEFAULT_DRIVER.z());
    }

    // ── 车轮初始化 ──

    /** 根据 VehicleType 初始化四个车轮。仅在首次 tick 或配置更改时调用。 */
    public void initWheels() {
        // 默认车轮位置（适用于约 2.0×4.0 的车辆）
        // localPos: (+X=右侧, +Z=前方, Y=垂直偏移)
        double wheelY = 0.35;
        VehicleType type = vehicleType != null ? vehicleType : VehicleType.DEFAULT;
        double wheelZ = type.getEffectiveWheelBase() * 0.5;
        double wheelX = type.getEffectiveTrackWidth() * 0.5;
        double wheelRadius = 0.4;

        this.wheels = new Wheel[4];
        // 前左 (FL)
        this.wheels[0] = new Wheel(new Vec3( wheelX, wheelY,  wheelZ), true,  true,  wheelRadius);
        // 前右 (FR)
        this.wheels[1] = new Wheel(new Vec3(-wheelX, wheelY,  wheelZ), true,  false, wheelRadius);
        // 后左 (RL)
        this.wheels[2] = new Wheel(new Vec3( wheelX, wheelY, -wheelZ), false, true,  wheelRadius);
        // 后右 (RR)
        this.wheels[3] = new Wheel(new Vec3(-wheelX, wheelY, -wheelZ), false, false, wheelRadius);

        this.wheelsInitialized = true;
    }

    // ── NBT 持久化 ──

    @Override
    protected void readAdditionalSaveData(CompoundTag tag) {
        this.speed = tag.getDouble("Speed");
        this.fuel = tag.contains("Fuel") ? tag.getDouble("Fuel") : this.maxFuel;
        this.gear = tag.contains("Gear") ? tag.getInt("Gear") : 1;
        this.steeringAngle = tag.getDouble("SteeringAngle");
        this.engineRPM = tag.contains("EngineRPM") ? tag.getDouble("EngineRPM") : 800;
        this.yawRate = tag.getDouble("YawRate");
        this.bodyPitch = tag.getDouble("BodyPitch");
        this.bodyRoll = tag.getDouble("BodyRoll");
        if (tag.contains("ObjPath")) entityData.set(DATA_OBJ_PATH, tag.getString("ObjPath"));
        if (tag.contains("ObjScale")) entityData.set(DATA_OBJ_SCALE, tag.getFloat("ObjScale"));
        if (tag.contains("DriverSeatX")) entityData.set(DATA_DRIVER_SEAT_X, tag.getFloat("DriverSeatX"));
        if (tag.contains("DriverSeatY")) entityData.set(DATA_DRIVER_SEAT_Y, tag.getFloat("DriverSeatY"));
        if (tag.contains("DriverSeatZ")) entityData.set(DATA_DRIVER_SEAT_Z, tag.getFloat("DriverSeatZ"));
        String typeKey = tag.getString("VehicleType");
        if (!typeKey.isEmpty()) entityData.set(DATA_VEHICLE_TYPE, typeKey);
    }

    @Override
    protected void addAdditionalSaveData(CompoundTag tag) {
        tag.putDouble("Speed", speed);
        tag.putDouble("Fuel", fuel);
        tag.putInt("Gear", gear);
        tag.putDouble("SteeringAngle", steeringAngle);
        tag.putDouble("EngineRPM", engineRPM);
        tag.putDouble("YawRate", yawRate);
        tag.putDouble("BodyPitch", bodyPitch);
        tag.putDouble("BodyRoll", bodyRoll);
        tag.putString("ObjPath", entityData.get(DATA_OBJ_PATH));
        tag.putFloat("ObjScale", entityData.get(DATA_OBJ_SCALE));
        tag.putFloat("DriverSeatX", entityData.get(DATA_DRIVER_SEAT_X));
        tag.putFloat("DriverSeatY", entityData.get(DATA_DRIVER_SEAT_Y));
        tag.putFloat("DriverSeatZ", entityData.get(DATA_DRIVER_SEAT_Z));
        tag.putString("VehicleType", entityData.get(DATA_VEHICLE_TYPE));
    }

    // ── 核心 Tick ──

    @Override
    public void tick() {
        super.tick();

        // 确保车轮已初始化
        if (!wheelsInitialized) {
            initWheels();
        }

        if (level().isClientSide) {
            updateClientVisualState();
        } else {
            // ── 重力（每 tick）──
            if (!isNoGravity()) {
                this.setDeltaMovement(this.getDeltaMovement().add(0.0, -0.08, 0.0));
            }

            if (isBeingDriven()) {
                VehiclePhysicsEngine.applyPhysics(this, level());
            } else {
                // 无人驾驶：自然减速 + 受重力下落
                speed *= 0.95;
                if (Math.abs(speed) < 0.001) speed = 0;
                throttleInput = moveToward(throttleInput, 0.0, 0.2);
                brakeInput = moveToward(brakeInput, 0.0, 0.2);
                steeringInput = moveToward(steeringInput, 0.0, 0.2);
                steeringAngle = moveToward(steeringAngle, 0.0, 5.0);
                yawRate *= 0.8;
                bodyPitchVelocity = (bodyPitchVelocity - bodyPitch * 0.18) * 0.68;
                bodyRollVelocity = (bodyRollVelocity - bodyRoll * 0.20) * 0.66;
                setBodyPitch(bodyPitch + bodyPitchVelocity);
                setBodyRoll(bodyRoll + bodyRollVelocity);
                setMaxUpStep(0.25f);
                this.move(MoverType.SELF, this.getDeltaMovement());
                this.setDeltaMovement(this.getDeltaMovement().multiply(0.5, 0.0, 0.5));
            }

            // 同步数据
            entityData.set(DATA_SPEED, (float) speed);
            entityData.set(DATA_FUEL, (float) fuel);
            entityData.set(DATA_GEAR, gear);
            entityData.set(DATA_ENGINE_RPM, (float) engineRPM);
            entityData.set(DATA_BODY_PITCH, (float) bodyPitch);
            entityData.set(DATA_BODY_ROLL, (float) bodyRoll);
            entityData.set(DATA_STEERING_ANGLE, (float) steeringAngle);
        }
    }

    private void updateClientVisualState() {
        applyClientTarget();
        updateClientVisualYaw();

        clientBodyPitchOld = clientBodyPitch;
        clientBodyRollOld = clientBodyRoll;
        float pitchTarget = clientBodyPitch + Mth.clamp(
                entityData.get(DATA_BODY_PITCH) - clientBodyPitch, -3.0f, 3.0f);
        float rollTarget = clientBodyRoll + Mth.clamp(
                entityData.get(DATA_BODY_ROLL) - clientBodyRoll, -4.0f, 4.0f);
        clientBodyPitch += (pitchTarget - clientBodyPitch) * 0.45f;
        clientBodyRoll += (rollTarget - clientBodyRoll) * 0.45f;
        speed = entityData.get(DATA_SPEED);
        fuel = entityData.get(DATA_FUEL);
        gear = entityData.get(DATA_GEAR);
        engineRPM = entityData.get(DATA_ENGINE_RPM);
        steeringAngle = entityData.get(DATA_STEERING_ANGLE);
    }

    private void updateClientVisualYaw() {
        if (!clientVisualYawInitialized) {
            resetClientVisualYaw(clientVisualYawResetPending
                    ? clientVisualYawTarget : this.getYRot());
            clientVisualYawResetPending = false;
            return;
        }

        clientVisualYawOld = clientVisualYaw;
        if (clientVisualYawResetPending) {
            resetClientVisualYaw(clientVisualYawTarget);
            clientVisualYawResetPending = false;
            return;
        }

        float error = Mth.wrapDegrees(clientVisualYawTarget - clientVisualYaw);
        clientVisualYawVelocity = Mth.clamp(
                clientVisualYawVelocity * 0.55f + error * 0.35f,
                -12.0f, 12.0f);
        if (Math.abs(error) < 0.02f && Math.abs(clientVisualYawVelocity) < 0.02f) {
            clientVisualYaw += error;
            clientVisualYawVelocity = 0.0f;
        } else {
            clientVisualYaw += clientVisualYawVelocity;
        }
        this.setYRot(clientVisualYaw);
    }

    private void resetClientVisualYaw(float yaw) {
        clientVisualYaw = yaw;
        clientVisualYawOld = yaw;
        clientVisualYawTarget = yaw;
        clientVisualYawVelocity = 0.0f;
        clientVisualYawInitialized = true;
        this.setYRot(yaw);
    }

    // ── 驾驶输入 ──

    public void handleDrivingInput(boolean accel, boolean brake, boolean left,
                                   boolean right, boolean handbrake) {
        this.accelerating = accel;
        this.braking = brake;
        this.steeringLeft = left;
        this.steeringRight = right;
        this.handbrake = handbrake;
    }

    // ── IVehicleDriveable 实现 ──

    @Override public double  getSpeed()       { return speed; }
    @Override public double  getMaxSpeed()    { return maxSpeed; }
    @Override public double  getFuel()        { return fuel; }
    @Override public double  getMaxFuel()     { return maxFuel; }
    @Override public int     getGear()        { return gear; }
    @Override public boolean isBeingDriven()  { return !getPassengers().isEmpty(); }
    @Override public void setAccelerating(boolean v) { this.accelerating = v; }
    @Override public void setBraking(boolean v)      { this.braking = v; }
    @Override public void setFuel(double f)           { this.fuel = Math.max(0, Math.min(f, maxFuel)); }
    @Override public void setSteering(boolean left, boolean right) {
        this.steeringLeft = left; this.steeringRight = right;
    }

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

    @Override
    public void setGear(int g) {
        this.gear = Math.max(-1, Math.min(g, 6));
    }

    // ── 新增物理接口 ──

    @Override public boolean isHandbrakeActive()  { return handbrake; }
    @Override public void setHandbrake(boolean v) { this.handbrake = v; }
    @Override public Wheel[] getWheels()          { return wheels; }
    @Override public double  getEngineRPM()       { return engineRPM; }
    @Override public void setEngineRPM(double r)  { this.engineRPM = r; }

    // ── 车辆属性 getter / setter ──

    public boolean isAccelerating()  { return accelerating; }
    public boolean isBraking()       { return braking; }
    public boolean isSteeringLeft()  { return steeringLeft; }
    public boolean isSteeringRight() { return steeringRight; }
    public double  getSteeringAngle(){ return steeringAngle; }
    public int     getAbsTimer()     { return absTimer; }
    public void    setAbsTimer(int t){ this.absTimer = t; }
    public double  getVehicleWeight(){ return vehicleWeight; }
    public double  getYawRate()      { return yawRate; }
    public double  getThrottleInput(){ return throttleInput; }
    public double  getBrakeInput()   { return brakeInput; }
    public double  getSteeringInput(){ return steeringInput; }
    public double  getRearGripFactor(){ return rearGripFactor; }
    public double  getBodyPitch()    { return bodyPitch; }
    public double  getBodyRoll()     { return bodyRoll; }
    public double  getBodyPitchVelocity() { return bodyPitchVelocity; }
    public double  getBodyRollVelocity()  { return bodyRollVelocity; }

    public void setSpeed(double s) { this.speed = Math.max(-maxSpeed * 0.3, Math.min(s, maxSpeed)); }
    public void setSteeringAngle(double a) {
        double limit = vehicleType != null
                ? vehicleType.getEffectiveMaxSteeringAngle() : 35.0;
        this.steeringAngle = Math.max(-limit, Math.min(a, limit));
    }
    public void setYawRate(double value) { this.yawRate = value; }
    public void setThrottleInput(double value) { this.throttleInput = clampUnit(value); }
    public void setBrakeInput(double value) { this.brakeInput = clampUnit(value); }
    public void setSteeringInput(double value) {
        this.steeringInput = Math.max(-1.0, Math.min(value, 1.0));
    }
    public void setRearGripFactor(double value) {
        this.rearGripFactor = Math.max(0.15, Math.min(value, 1.0));
    }
    public void setBodyPitch(double value) { this.bodyPitch = clamp(value, -15.0, 15.0); }
    public void setBodyRoll(double value) { this.bodyRoll = clamp(value, -18.0, 18.0); }
    public void setBodyPitchVelocity(double value) { this.bodyPitchVelocity = value; }
    public void setBodyRollVelocity(double value) { this.bodyRollVelocity = value; }

    public float getVisualBodyPitch(float partialTick) {
        return clientBodyPitchOld + (clientBodyPitch - clientBodyPitchOld) * partialTick;
    }

    public float getVisualBodyRoll(float partialTick) {
        return clientBodyRollOld + (clientBodyRoll - clientBodyRollOld) * partialTick;
    }

    public float getVisualYaw(float partialTick) {
        if (!level().isClientSide || !clientVisualYawInitialized) {
            return this.getYRot();
        }
        return clientVisualYawOld
                + (clientVisualYaw - clientVisualYawOld) * partialTick;
    }

    public void setVehicleType(VehicleType type) {
        this.vehicleType = type;
        if (type != null) {
            maxSpeed = type.maxSpeed();
            maxFuel = type.fuelCapacity();
            fuel = maxFuel;
            vehicleWeight = type.weight();
            wheelsInitialized = false;
            // 同步 OBJ 渲染参数到客户端
            entityData.set(DATA_OBJ_PATH, type.objModelPath() != null ? type.objModelPath() : "");
            entityData.set(DATA_OBJ_SCALE, (float) type.objScale());
            SeatConfig driverSeat = type.getDriverSeat();
            entityData.set(DATA_DRIVER_SEAT_X, (float) driverSeat.x());
            entityData.set(DATA_DRIVER_SEAT_Y, (float) driverSeat.y());
            entityData.set(DATA_DRIVER_SEAT_Z, (float) driverSeat.z());
        }
    }

    public VehicleType getVehicleTypeConfig() { return vehicleType; }

    /** 客户端安全：从 entityData 读取 OBJ 模型路径（已同步）。 */
    public String getSyncedObjPath() {
        return entityData.get(DATA_OBJ_PATH);
    }

    /** 客户端安全：从 entityData 读取 OBJ 缩放（已同步）。 */
    public float getSyncedObjScale() {
        return entityData.get(DATA_OBJ_SCALE);
    }

    public Vec3 getDriverSeatOffset() {
        return new Vec3(
                entityData.get(DATA_DRIVER_SEAT_X),
                entityData.get(DATA_DRIVER_SEAT_Y),
                entityData.get(DATA_DRIVER_SEAT_Z));
    }

    public Vec3 getRiderWorldPosition(float partialTick) {
        Vec3 localOffset = getDriverSeatOffset();
        double pitch = Math.toRadians(level().isClientSide
                ? getVisualBodyPitch(partialTick) : bodyPitch);
        double roll = Math.toRadians(level().isClientSide
                ? getVisualBodyRoll(partialTick) : bodyRoll);

        double x1 = localOffset.x * Math.cos(roll) - localOffset.y * Math.sin(roll);
        double y1 = localOffset.x * Math.sin(roll) + localOffset.y * Math.cos(roll);
        double z1 = localOffset.z;
        double y2 = y1 * Math.cos(pitch) - z1 * Math.sin(pitch);
        double z2 = y1 * Math.sin(pitch) + z1 * Math.cos(pitch);

        double yaw = Math.toRadians(level().isClientSide
                ? getVisualYaw(partialTick) : this.getYRot());
        double worldX = x1 * Math.cos(yaw) - z2 * Math.sin(yaw);
        double worldZ = x1 * Math.sin(yaw) + z2 * Math.cos(yaw);
        return position().add(worldX, y2, worldZ);
    }

    /** 获取当前车辆配置的 key（用于 spawn item NBT）。 */
    public String getVehicleTypeKey() {
        String key = entityData.get(DATA_VEHICLE_TYPE);
        return key != null && !key.isEmpty() ? key : "sports_car";
    }

    // ── 骑乘 / 交互 ──

    /**
     * 驾驶中延迟应用位置，等 baseTick() 保存 xOld 后再设新位置。
     * <p>
     * MC 渲染：lerp(partialTick, xOld, getX())。关键问题是网络包处理
     * 在 baseTick *之前*——如果 lerpTo 里立即 setPos，baseTick 随后会把
     * xOld 覆写为新值，导致 xOld==getX() → 插值空间消失 → 抽搐。
     * <p>
     * 解决：仅暂存目标，等 Phase.END（baseTick 之后）由
     * {@link #applyClientTarget()} 应用 → xOld 为旧值，getX 为新值 → 60fps 平滑。
     */
    @Override
    public void lerpTo(double x, double y, double z, float yRot, float xRot,
                       int steps, boolean teleport) {
        if (level().isClientSide) {
            double dx = x - this.getX();
            double dy = y - this.getY();
            double dz = z - this.getZ();
            float yawError = clientVisualYawInitialized
                    ? Mth.wrapDegrees(yRot - clientVisualYaw) : 0.0f;
            boolean hardReset = teleport
                    || dx * dx + dy * dy + dz * dz > 64.0
                    || Math.abs(yawError) > 100.0f;

            this.clientTargetX = x;
            this.clientTargetY = y;
            this.clientTargetZ = z;
            this.clientTargetXRot = xRot;
            this.hasClientTarget = true;

            if (!clientVisualYawInitialized || hardReset) {
                this.clientVisualYawTarget = yRot;
                this.clientVisualYawResetPending = true;
            } else {
                this.clientVisualYawTarget = clientVisualYaw
                        + Mth.wrapDegrees(yRot - clientVisualYaw);
            }
            return;
        }
        super.lerpTo(x, y, z, yRot, xRot, steps, teleport);
    }

    @Override protected boolean canRide(Entity e) { return false; }

    @Override
    public InteractionResult interact(Player player, InteractionHand hand) {
        ItemStack heldItem = player.getItemInHand(hand);

        if (!level().isClientSide) {
            // ── 拆卸扳手：将载具变回生成器掉落物 ──
            if (heldItem.getItem() instanceof VehicleDismantleItem) {
                ejectPassengers();
                ItemStack spawnStack = ModItemRegistry.createVehicleSpawnStack(getVehicleTypeKey());
                this.spawnAtLocation(spawnStack);
                this.discard();
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

    // ── 乘客位置 ──

    @Override
    public double getPassengersRidingOffset() {
        return getDriverSeatOffset().y;
    }

    @Override
    protected void positionRider(Entity passenger, MoveFunction moveFunc) {
        if (this.hasPassenger(passenger)) {
            Vec3 worldPos = getRiderWorldPosition(1.0f);
            passenger.setPos(worldPos.x, worldPos.y, worldPos.z);
        } else {
            super.positionRider(passenger, moveFunc);
        }
    }

    // ── 碰撞箱 ──

    @Override
    public EntityDimensions getDimensions(Pose pose) {
        // 车辆大小：宽 2.2 方块，高 1.5 方块
        return EntityDimensions.scalable(2.2f, 1.5f);
    }

    private static double clampUnit(double value) {
        return Math.max(0.0, Math.min(value, 1.0));
    }

    private static double clamp(double value, double min, double max) {
        return Math.max(min, Math.min(value, max));
    }

    private static double moveToward(double current, double target, double maxDelta) {
        if (current < target) return Math.min(current + maxDelta, target);
        if (current > target) return Math.max(current - maxDelta, target);
        return current;
    }
}
