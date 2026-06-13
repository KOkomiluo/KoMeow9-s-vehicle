package com.yourname.vehicleframework.client.input;

import com.yourname.vehicleframework.VehicleFramework;
import com.yourname.vehicleframework.common.entity.VehicleEntity;
import com.yourname.vehicleframework.common.network.VehicleDrivingPacket;

import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.player.Player;

import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

@Mod.EventBusSubscriber(modid = VehicleFramework.MOD_ID, value = Dist.CLIENT)
public final class VehicleKeyInputHandler {

    private VehicleKeyInputHandler() {}

    private static boolean lastAccel, lastBrake, lastLeft, lastRight, lastHandbrake;

    @SubscribeEvent
    public static void onClientTick(final TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;

        Minecraft mc = Minecraft.getInstance();
        Player player = mc.player;
        if (player == null || mc.level == null) return;

        if (!(player.getVehicle() instanceof VehicleEntity vehicle)) {
            resetState();
            return;
        }

        boolean accel = mc.options.keyUp.isDown();
        boolean brake = mc.options.keyDown.isDown();
        boolean left  = mc.options.keyLeft.isDown();
        boolean right = mc.options.keyRight.isDown();
        boolean handbrake = mc.options.keyJump.isDown(); // 空格 = 手刹

        if (accel != lastAccel || brake != lastBrake
                || left != lastLeft || right != lastRight
                || handbrake != lastHandbrake) {
            VehicleDrivingPacket.send(vehicle.getId(), accel, brake, left, right, handbrake);
            lastAccel = accel;
            lastBrake = brake;
            lastLeft = left;
            lastRight = right;
            lastHandbrake = handbrake;
        }
    }

    private static void resetState() {
        lastAccel = lastBrake = lastLeft = lastRight = lastHandbrake = false;
    }
}
