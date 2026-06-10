package com.yourname.vehicleframework.client.camera;

import com.yourname.vehicleframework.VehicleFramework;
import com.yourname.vehicleframework.common.entity.VehicleEntity;

import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.player.Player;

import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.ViewportEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

@Mod.EventBusSubscriber(modid = VehicleFramework.MOD_ID, value = Dist.CLIENT)
public final class VehicleCameraHandler {

    private VehicleCameraHandler() {}

    private static final float SPEED_FOV_BOOST = 15.0f;

    @SubscribeEvent
    public static void onComputeFov(final ViewportEvent.ComputeFov event) {
        Player player = Minecraft.getInstance().player;
        if (player == null) return;

        if (player.getVehicle() instanceof VehicleEntity vehicle) {
            double speedRatio = Math.min(Math.abs(vehicle.getSpeed()) / vehicle.getMaxSpeed(), 1.0);
            event.setFOV(event.getFOV() + (float)(speedRatio * SPEED_FOV_BOOST));
        }
    }

    public static void resetCamera() {}
}
