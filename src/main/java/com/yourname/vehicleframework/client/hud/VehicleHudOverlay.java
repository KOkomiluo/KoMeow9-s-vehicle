package com.yourname.vehicleframework.client.hud;

import com.yourname.vehicleframework.common.entity.VehicleEntity;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;

import net.minecraftforge.client.event.RegisterGuiOverlaysEvent;
import net.minecraftforge.client.gui.overlay.IGuiOverlay;
import net.minecraftforge.client.gui.overlay.VanillaGuiOverlay;

import java.util.List;

/** 驾驶时显示在左下角的简易 F3 风格信息面板。 */
public final class VehicleHudOverlay {

    private VehicleHudOverlay() {}

    private static final int MARGIN = 4;
    private static final int PADDING = 3;
    private static final int LINE_HEIGHT = 10;
    private static final int TEXT_COLOR = 0xFFFFFFFF;
    private static final int BACKGROUND_COLOR = 0x90000000;

    private static final IGuiOverlay OVERLAY = VehicleHudOverlay::render;

    public static void register(RegisterGuiOverlaysEvent event) {
        event.registerAbove(VanillaGuiOverlay.HOTBAR.id(), "vehicle_info", OVERLAY);
    }

    private static void render(
            net.minecraftforge.client.gui.overlay.ForgeGui gui,
            GuiGraphics graphics,
            float partialTick,
            int screenWidth,
            int screenHeight) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.options.hideGui || minecraft.player == null) return;
        if (!(minecraft.player.getVehicle() instanceof VehicleEntity vehicle)) return;

        int speedKmh = (int) Math.round(Math.abs(vehicle.getSpeed()) * 72.0);
        double maxFuel = Math.max(0.001, vehicle.getMaxFuel());
        int fuelPercent = (int) Math.round(
                Math.max(0.0, Math.min(100.0, vehicle.getFuel() / maxFuel * 100.0)));

        List<Component> lines = List.of(
                Component.translatable("hud.vehicleframework.speed", speedKmh),
                Component.translatable("hud.vehicleframework.fuel", fuelPercent),
                Component.translatable("hud.vehicleframework.gear", vehicle.getGearDisplayName()),
                Component.translatable(
                        "hud.vehicleframework.mode",
                        vehicle.isManualGearControl() ? "MT" : "AT")
        );

        Font font = minecraft.font;
        int textWidth = 0;
        for (Component line : lines) {
            textWidth = Math.max(textWidth, font.width(line));
        }

        int panelWidth = textWidth + PADDING * 2;
        int panelHeight = lines.size() * LINE_HEIGHT + PADDING * 2;
        int x = MARGIN;
        int y = screenHeight - MARGIN - panelHeight;

        graphics.fill(x, y, x + panelWidth, y + panelHeight, BACKGROUND_COLOR);
        for (int i = 0; i < lines.size(); i++) {
            graphics.drawString(
                    font,
                    lines.get(i),
                    x + PADDING,
                    y + PADDING + i * LINE_HEIGHT,
                    TEXT_COLOR,
                    false);
        }
    }
}
