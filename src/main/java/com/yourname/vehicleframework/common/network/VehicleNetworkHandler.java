package com.yourname.vehicleframework.common.network;

import com.yourname.vehicleframework.VehicleFramework;

import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.network.NetworkRegistry;
import net.minecraftforge.network.simple.SimpleChannel;

public final class VehicleNetworkHandler {

    private VehicleNetworkHandler() {}

    private static final String PROTOCOL_VERSION = "1";

    public static final SimpleChannel CHANNEL = NetworkRegistry.newSimpleChannel(
            new ResourceLocation(VehicleFramework.MOD_ID, "main"),
            () -> PROTOCOL_VERSION,
            PROTOCOL_VERSION::equals,
            PROTOCOL_VERSION::equals
    );

    private static int packetId = 0;

    public static void registerPackets() {
        CHANNEL.messageBuilder(VehicleDrivingPacket.class, packetId++)
                .encoder(VehicleDrivingPacket::encode)
                .decoder(VehicleDrivingPacket::new)
                .consumerMainThread(VehicleDrivingPacket::handle)
                .add();
    }
}
