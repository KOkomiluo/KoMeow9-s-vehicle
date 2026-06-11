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

    private static boolean lastAccel, lastBrake, lastLeft, lastRight;

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

        if (accel != lastAccel || brake != lastBrake || left != lastLeft || lastRight != right) {
            VehicleDrivingPacket.send(vehicle.getId(), accel, brake, left, right);
            lastAccel = accel; lastBrake = brake; lastLeft = left; lastRight = right;
        }
    }

    private static void resetState() {
        lastAccel = lastBrake = lastLeft = lastRight = false;
    }
}
