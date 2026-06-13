package com.yourname.vehicleframework.api;

import com.yourname.vehicleframework.common.physics.Wheel;

import net.minecraft.world.entity.player.Player;

/**
 * 可驾驶车辆的公开 API 接口。
 * <p>
 * 子 Mod 可通过此接口与车辆实体交互，而无需直接依赖具体实现类。
 */
public interface IVehicleDriveable {

    /** 获取车辆当前速度（方块/刻）。 */
    double getSpeed();

    /** 获取车辆最大速度（方块/刻）。 */
    double getMaxSpeed();

    /** 设置油门状态。 */
    void setAccelerating(boolean accelerating);

    /** 设置刹车状态。 */
    void setBraking(boolean braking);

    /** 设置转向输入。 */
    void setSteering(boolean left, boolean right);

    /** 获取当前驾驶员。 */
    Player getDriver();

    /** 车辆是否有人驾驶。 */
    boolean isBeingDriven();

    /** 获取当前燃料值。 */
    double getFuel();

    /** 设置燃料值。 */
    void setFuel(double fuel);

    /** 获取燃料最大值。 */
    double getMaxFuel();

    /** 获取当前档位。 */
    int getGear();

    /** 切换档位。 */
    void shiftGear(boolean up);

    /** 直接设置档位（-1=R, 0=N, 1-6=前进挡）。 */
    void setGear(int gear);

    // ── 新增物理接口 ──

    /** 手刹是否激活（漂移用）。 */
    boolean isHandbrakeActive();

    /** 设置手刹状态。 */
    void setHandbrake(boolean active);

    /** 获取四个车轮。 */
    Wheel[] getWheels();

    /** 获取当前发动机转速（RPM）。 */
    double getEngineRPM();

    /** 设置发动机转速。 */
    void setEngineRPM(double rpm);
}
