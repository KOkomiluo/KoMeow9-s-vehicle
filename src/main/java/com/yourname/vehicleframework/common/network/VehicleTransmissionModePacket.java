package com.yourname.vehicleframework.common.network;

import com.yourname.vehicleframework.common.entity.VehicleEntity;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;

import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

/** 在 AT 与 MT 模式之间切换。 */
public final class VehicleTransmissionModePacket {

    private final int entityId;

    public VehicleTransmissionModePacket(int entityId) {
        this.entityId = entityId;
    }

    public VehicleTransmissionModePacket(FriendlyByteBuf buf) {
        this.entityId = buf.readVarInt();
    }

    public void encode(FriendlyByteBuf buf) {
        buf.writeVarInt(entityId);
    }

    public void handle(Supplier<NetworkEvent.Context> supplier) {
        NetworkEvent.Context context = supplier.get();
        context.enqueueWork(() -> {
            ServerPlayer player = context.getSender();
            if (player == null) return;

            Entity entity = player.level().getEntity(entityId);
            if (!(entity instanceof VehicleEntity vehicle)
                    || player.getVehicle() != vehicle
                    || vehicle.getDriver() != player) {
                return;
            }

            boolean manual = vehicle.toggleTransmissionMode();
            player.displayClientMessage(Component.translatable(
                    manual
                            ? "message.vehicleframework.transmission_mt"
                            : "message.vehicleframework.transmission_at"), true);
        });
        context.setPacketHandled(true);
    }

    public static void send(int entityId) {
        VehicleNetworkHandler.CHANNEL.sendToServer(
                new VehicleTransmissionModePacket(entityId));
    }
}
