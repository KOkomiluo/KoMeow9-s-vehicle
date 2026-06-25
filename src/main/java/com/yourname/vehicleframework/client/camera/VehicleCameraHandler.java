package com.yourname.vehicleframework.client.camera;

import com.mojang.blaze3d.platform.InputConstants;
import com.yourname.vehicleframework.VehicleFramework;
import com.yourname.vehicleframework.common.entity.VehicleEntity;

import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.InputEvent;
import net.minecraftforge.client.event.ViewportEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import org.joml.Vector3f;
import org.lwjgl.glfw.GLFW;

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
    private static final double DEFAULT_CAMERA_DISTANCE = 4.0;
    private static final double MIN_CAMERA_DISTANCE = 2.0;
    private static final double MAX_CAMERA_DISTANCE = 12.0;
    private static final double CAMERA_DISTANCE_STEP = 0.75;

    private static double cameraDistance = DEFAULT_CAMERA_DISTANCE;

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

    @SubscribeEvent
    public static void onMouseScroll(final InputEvent.MouseScrollingEvent event) {
        Minecraft mc = Minecraft.getInstance();
        Player player = mc.player;
        if (player == null || mc.screen != null) return;
        if (!(player.getVehicle() instanceof VehicleEntity)) return;
        if (mc.options.getCameraType().isFirstPerson()) return;
        if (!isControlDown(mc)) return;

        cameraDistance = Mth.clamp(
                cameraDistance - event.getScrollDelta() * CAMERA_DISTANCE_STEP,
                MIN_CAMERA_DISTANCE,
                MAX_CAMERA_DISTANCE);
        event.setCanceled(true);
    }

    @SubscribeEvent
    public static void onComputeCameraAngles(final ViewportEvent.ComputeCameraAngles event) {
        Minecraft mc = Minecraft.getInstance();
        Player player = mc.player;
        if (player == null || !(player.getVehicle() instanceof VehicleEntity)) return;
        if (mc.options.getCameraType().isFirstPerson()) return;

        Camera camera = event.getCamera();
        if (camera.getEntity() != player) return;

        Vec3 focus = player.getEyePosition((float) event.getPartialTick());
        Vector3f look = camera.getLookVector();
        double distance = getMaxZoom(player.level(), player, focus, look, cameraDistance);

        camera.setPosition(focus.subtract(
                look.x() * distance,
                look.y() * distance,
                look.z() * distance));
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

        // 2. 使用与服务端完全相同的配置化驾驶位。
        var riderPosition = vehicle.getRiderWorldPosition(1.0f);

        // 3. 设置玩家位置（60fps 平滑跟随）— 不锁定 yaw，让玩家自由环顾
        player.setPos(riderPosition.x, riderPosition.y, riderPosition.z);
        // 注意：不要 setYRot(vehicle.getYRot()) — 这会锁定摄像机视角，阻止自由环顾
    }

    public static void resetCamera() {}

    private static boolean isControlDown(Minecraft mc) {
        long window = mc.getWindow().getWindow();
        return InputConstants.isKeyDown(window, GLFW.GLFW_KEY_LEFT_CONTROL)
                || InputConstants.isKeyDown(window, GLFW.GLFW_KEY_RIGHT_CONTROL);
    }

    private static double getMaxZoom(BlockGetter level, Entity entity, Vec3 focus,
                                     Vector3f forwards, double startingDistance) {
        double distance = startingDistance;
        for (int i = 0; i < 8; ++i) {
            float xOffset = (float) ((i & 1) * 2 - 1) * 0.1F;
            float yOffset = (float) ((i >> 1 & 1) * 2 - 1) * 0.1F;
            float zOffset = (float) ((i >> 2 & 1) * 2 - 1) * 0.1F;

            Vec3 from = focus.add(xOffset, yOffset, zOffset);
            Vec3 to = new Vec3(
                    focus.x - forwards.x() * distance + xOffset,
                    focus.y - forwards.y() * distance + yOffset,
                    focus.z - forwards.z() * distance + zOffset);
            HitResult hit = level.clip(new ClipContext(
                    from,
                    to,
                    ClipContext.Block.VISUAL,
                    ClipContext.Fluid.NONE,
                    entity));
            if (hit.getType() != HitResult.Type.MISS) {
                double hitDistance = hit.getLocation().distanceTo(focus);
                if (hitDistance < distance) {
                    distance = hitDistance;
                }
            }
        }
        return distance;
    }
}
