package com.yourname.vehicleframework.client.camera;

import com.yourname.vehicleframework.VehicleFramework;
import com.yourname.vehicleframework.common.entity.VehicleEntity;

import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;

import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.ViewportEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/**
 * 车辆摄像机处理 — 60fps 平滑 + 速度 FOV 动态缩放。
 * <p>
 * 核心机制：服务端 20 TPS 跑物理，客户端收到位置包后在
 * {@link #onClientTick} 里用车辆当前插值位置重算玩家骑乘位置，
 * MC 渲染管线自带的 lerp(partialTick, xOld, getX()) 负责 60fps 平滑。
 */
@Mod.EventBusSubscriber(modid = VehicleFramework.MOD_ID, value = Dist.CLIENT)
public final class VehicleCameraHandler {

    private VehicleCameraHandler() {}

    private static final float SPEED_FOV_BOOST = 15.0f;

    // ── FOV 动态缩放 ──

    @SubscribeEvent
    public static void onComputeFov(final ViewportEvent.ComputeFov event) {
        Player player = Minecraft.getInstance().player;
        if (player == null) return;

        if (player.getVehicle() instanceof VehicleEntity vehicle) {
            double speedRatio = Math.min(Math.abs(vehicle.getSpeed()) / vehicle.getMaxSpeed(), 1.0);
            event.setFOV(event.getFOV() + (float) (speedRatio * SPEED_FOV_BOOST));
        }
    }

    // ── 骑乘摄像机位置 60fps 同步 ──

    /**
     * 在每 tick 尾（包处理 + 实体 tick 之后，渲染之前）把玩家位置
     * 锁定到车辆的当前插值位置 + 骑乘偏移。
     * <p>
     * 选 Phase.END 是因为：
     * <ol>
     *   <li>服务端位置包已经在 tick 中处理完毕</li>
     *   <li>Entity.baseTick() 已调用 setOldPosAndRot() 保存 xOld</li>
     *   <li>此时 setPos → getX() 变化，但 xOld 不变 → 渲染插值正确</li>
     * </ol>
     */
    @SubscribeEvent
    public static void onClientTick(final TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;

        Player player = Minecraft.getInstance().player;
        if (player == null) return;
        if (!(player.getVehicle() instanceof VehicleEntity vehicle)) return;

        // 1. 应用车辆延迟位置（baseTick 已保存 xOld，现在 setPos 不会破坏插值）
        vehicle.applyClientTarget();

        // 2. 计算骑乘位置（与 positionRider 服务端逻辑一致）
        Vec3 offset = new Vec3(0.5, vehicle.getPassengersRidingOffset(), -0.2);
        float yawRad = (float) Math.toRadians(vehicle.getYRot());
        double cos = Math.cos(yawRad);
        double sin = Math.sin(yawRad);
        double rx = offset.x * cos - offset.z * sin;
        double rz = offset.x * sin + offset.z * cos;

        // 3. 设置玩家位置和朝向 → renderer 用 lerp(partialTick, xOld, getX()) 60fps 平滑
        player.setPos(
                vehicle.getX() + rx,
                vehicle.getY() + offset.y,
                vehicle.getZ() + rz);
        player.setYRot(vehicle.getYRot());      // 摄像机方向跟随车辆 yaw
    }

    public static void resetCamera() {}
}
