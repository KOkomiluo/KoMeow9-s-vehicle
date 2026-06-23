package com.yourname.vehicleframework.common.network;

import com.yourname.vehicleframework.common.entity.VehicleEntity;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;

import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

/** 单次手动换挡请求。与连续驾驶输入分开，避免长按导致连续跳挡。 */
public final class VehicleGearShiftPacket {

    private final int entityId;
    private final boolean shiftUp;

    public VehicleGearShiftPacket(int entityId, boolean shiftUp) {
        this.entityId = entityId;
        this.shiftUp = shiftUp;
    }

    public VehicleGearShiftPacket(FriendlyByteBuf buf) {
        this.entityId = buf.readVarInt();
        this.shiftUp = buf.readBoolean();
    }

    public void encode(FriendlyByteBuf buf) {
        buf.writeVarInt(entityId);
        buf.writeBoolean(shiftUp);
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

            if (vehicle.requestManualGearShift(shiftUp)) {
                player.displayClientMessage(Component.translatable(
                        vehicle.isManualGearControl()
                                ? "message.vehicleframework.gear_changed_mt"
                                : "message.vehicleframework.gear_changed_at",
                        vehicle.getGearDisplayName()), true);
            } else {
                player.displayClientMessage(Component.translatable(
                        "message.vehicleframework.gear_rejected"), true);
            }
        });
        context.setPacketHandled(true);
    }

    public static void send(int entityId, boolean shiftUp) {
        VehicleNetworkHandler.CHANNEL.sendToServer(
                new VehicleGearShiftPacket(entityId, shiftUp));
    }
}
