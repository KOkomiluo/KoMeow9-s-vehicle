package com.yourname.vehicleframework.client;

import com.yourname.vehicleframework.VehicleFramework;
import com.yourname.vehicleframework.client.hud.VehicleHudOverlay;
import com.yourname.vehicleframework.client.input.VehicleKeyInputHandler;
import com.yourname.vehicleframework.client.render.VehicleRenderer;
import com.yourname.vehicleframework.common.registry.ModEntityRegistry;

import net.minecraftforge.client.event.EntityRenderersEvent;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.fml.event.lifecycle.FMLClientSetupEvent;

public class VehicleFrameworkClient {

    public static void init(IEventBus modEventBus) {
        modEventBus.addListener(VehicleFrameworkClient::onClientSetup);
        modEventBus.addListener(VehicleFrameworkClient::onRegisterRenderers);
        modEventBus.addListener(VehicleKeyInputHandler::registerKeyMappings);
        modEventBus.addListener(VehicleHudOverlay::register);
    }

    private static void onClientSetup(final FMLClientSetupEvent event) {
        VehicleFramework.LOGGER.info("Vehicle Framework client setup...");
    }

    private static void onRegisterRenderers(final EntityRenderersEvent.RegisterRenderers event) {
        event.registerEntityRenderer(ModEntityRegistry.VEHICLE.get(), VehicleRenderer::new);
    }
}
