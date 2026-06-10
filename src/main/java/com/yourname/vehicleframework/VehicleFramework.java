package com.yourname.vehicleframework;

import com.yourname.vehicleframework.client.VehicleFrameworkClient;
import com.yourname.vehicleframework.common.network.VehicleNetworkHandler;
import com.yourname.vehicleframework.common.registry.ModEntityRegistry;
import com.yourname.vehicleframework.common.registry.ModItemRegistry;
import com.yourname.vehicleframework.data.VehicleConfigLoader;

import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.server.ServerStartingEvent;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.event.lifecycle.FMLCommonSetupEvent;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Mod(VehicleFramework.MOD_ID)
public class VehicleFramework {

    public static final String MOD_ID = "vehicleframework";
    public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

    public VehicleFramework(IEventBus modEventBus) {
        LOGGER.info("Vehicle Framework initializing...");

        ModEntityRegistry.ENTITY_TYPES.register(modEventBus);
        ModItemRegistry.ITEMS.register(modEventBus);

        modEventBus.addListener(this::commonSetup);

        // 仅在客户端初始化
        DistExecutor.unsafeRunWhenOn(Dist.CLIENT,
                () -> () -> VehicleFrameworkClient.init(modEventBus));

        MinecraftForge.EVENT_BUS.addListener(this::onServerStarting);

        LOGGER.info("Vehicle Framework initialization complete.");
    }

    private void commonSetup(final FMLCommonSetupEvent event) {
        event.enqueueWork(() -> {
            VehicleNetworkHandler.registerPackets();
            LOGGER.info("Network packets registered.");
        });
    }

    private void onServerStarting(final ServerStartingEvent event) {
        VehicleConfigLoader.loadAllConfigs(event.getServer());
        LOGGER.info("Vehicle configs loaded.");
    }
}
