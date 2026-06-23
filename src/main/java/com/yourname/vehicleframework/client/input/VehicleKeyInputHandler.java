package com.yourname.vehicleframework.client.input;

import com.mojang.blaze3d.platform.InputConstants;
import com.yourname.vehicleframework.VehicleFramework;
import com.yourname.vehicleframework.common.entity.VehicleEntity;
import com.yourname.vehicleframework.common.network.VehicleDrivingPacket;
import com.yourname.vehicleframework.common.network.VehicleGearShiftPacket;
import com.yourname.vehicleframework.common.network.VehicleTransmissionModePacket;

import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.player.Player;

import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RegisterKeyMappingsEvent;
import net.minecraftforge.client.settings.IKeyConflictContext;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import org.lwjgl.glfw.GLFW;

@Mod.EventBusSubscriber(modid = VehicleFramework.MOD_ID, value = Dist.CLIENT)
public final class VehicleKeyInputHandler {

    private VehicleKeyInputHandler() {}

    public static final String KEY_CATEGORY = "key.categories.vehicleframework";

    private static final IKeyConflictContext DRIVING_CONTEXT = new IKeyConflictContext() {
        @Override
        public boolean isActive() {
            Minecraft mc = Minecraft.getInstance();
            return mc.screen == null
                    && mc.player != null
                    && mc.player.getVehicle() instanceof VehicleEntity;
        }

        @Override
        public boolean conflicts(IKeyConflictContext other) {
            return other == this;
        }
    };

    public static final KeyMapping SHIFT_UP = new KeyMapping(
            "key.vehicleframework.shift_up", DRIVING_CONTEXT,
            InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_PAGE_UP, KEY_CATEGORY);

    public static final KeyMapping SHIFT_DOWN = new KeyMapping(
            "key.vehicleframework.shift_down", DRIVING_CONTEXT,
            InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_PAGE_DOWN, KEY_CATEGORY);

    public static final KeyMapping TOGGLE_TRANSMISSION_MODE = new KeyMapping(
            "key.vehicleframework.toggle_transmission_mode", DRIVING_CONTEXT,
            InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_RIGHT_CONTROL, KEY_CATEGORY);

    private static boolean lastAccel, lastBrake, lastLeft, lastRight, lastHandbrake;

    public static void registerKeyMappings(RegisterKeyMappingsEvent event) {
        event.register(SHIFT_UP);
        event.register(SHIFT_DOWN);
        event.register(TOGGLE_TRANSMISSION_MODE);
    }

    @SubscribeEvent
    public static void onClientTick(final TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;

        Minecraft mc = Minecraft.getInstance();
        Player player = mc.player;
        if (player == null || mc.level == null) return;

        if (!(player.getVehicle() instanceof VehicleEntity vehicle)) {
            while (SHIFT_UP.consumeClick()) {}
            while (SHIFT_DOWN.consumeClick()) {}
            while (TOGGLE_TRANSMISSION_MODE.consumeClick()) {}
            resetState();
            return;
        }

        while (SHIFT_UP.consumeClick()) {
            VehicleGearShiftPacket.send(vehicle.getId(), true);
        }
        while (SHIFT_DOWN.consumeClick()) {
            VehicleGearShiftPacket.send(vehicle.getId(), false);
        }
        while (TOGGLE_TRANSMISSION_MODE.consumeClick()) {
            VehicleTransmissionModePacket.send(vehicle.getId());
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
