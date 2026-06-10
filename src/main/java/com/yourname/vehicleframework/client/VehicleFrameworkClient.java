package com.yourname.vehicleframework.client;

import com.yourname.vehicleframework.VehicleFramework;
import com.yourname.vehicleframework.client.render.SmartVehicleRenderer;
import com.yourname.vehicleframework.common.registry.ModEntityRegistry;

import net.minecraftforge.client.event.EntityRenderersEvent;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.fml.event.lifecycle.FMLClientSetupEvent;

public class VehicleFrameworkClient {

    public static void init(IEventBus modEventBus) {
        modEventBus.addListener(VehicleFrameworkClient::onClientSetup);
        modEventBus.addListener(VehicleFrameworkClient::onRegisterRenderers);
    }

    private static void onClientSetup(final FMLClientSetupEvent event) {
        VehicleFramework.LOGGER.info("Vehicle Framework client setup...");
    }

    private static void onRegisterRenderers(final EntityRenderersEvent.RegisterRenderers event) {
        // 智能渲染器：有 objModelPath 时用 OBJ，否则用 GeckoLib 骨骼动画
        event.registerEntityRenderer(ModEntityRegistry.VEHICLE.get(), SmartVehicleRenderer::new);
    }
}
