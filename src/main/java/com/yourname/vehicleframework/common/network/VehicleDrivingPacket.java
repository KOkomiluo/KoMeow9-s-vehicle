package com.yourname.vehicleframework.common.network;

import com.yourname.vehicleframework.common.entity.VehicleEntity;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;

import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

/**
 * WASD + 手刹驾驶输入同步封包 (Forge 1.20.1 SimpleChannel 版本)。
 */
public class VehicleDrivingPacket {

    private final int entityId;
    private final boolean accelerating;
    private final boolean braking;
    private final boolean steeringLeft;
    private final boolean steeringRight;
    private final boolean handbrake;

    public VehicleDrivingPacket(int entityId, boolean accelerating, boolean braking,
                                boolean steeringLeft, boolean steeringRight, boolean handbrake) {
        this.entityId = entityId;
        this.accelerating = accelerating;
        this.braking = braking;
        this.steeringLeft = steeringLeft;
        this.steeringRight = steeringRight;
        this.handbrake = handbrake;
    }

    public VehicleDrivingPacket(FriendlyByteBuf buf) {
        this.entityId = buf.readInt();
        this.accelerating = buf.readBoolean();
        this.braking = buf.readBoolean();
        this.steeringLeft = buf.readBoolean();
        this.steeringRight = buf.readBoolean();
        this.handbrake = buf.readBoolean();
    }

    public void encode(FriendlyByteBuf buf) {
        buf.writeInt(entityId);
        buf.writeBoolean(accelerating);
        buf.writeBoolean(braking);
        buf.writeBoolean(steeringLeft);
        buf.writeBoolean(steeringRight);
        buf.writeBoolean(handbrake);
    }

    public void handle(Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> {
            ServerPlayer player = ctx.get().getSender();
            if (player != null) {
                Entity entity = player.level().getEntity(entityId);
                if (entity instanceof VehicleEntity vehicle) {
                    vehicle.handleDrivingInput(accelerating, braking, steeringLeft, steeringRight, handbrake);
                }
            }
        });
        ctx.get().setPacketHandled(true);
    }

    public static void send(int entityId, boolean accel, boolean brake,
                            boolean left, boolean right, boolean handbrake) {
        VehicleNetworkHandler.CHANNEL.sendToServer(
                new VehicleDrivingPacket(entityId, accel, brake, left, right, handbrake));
    }
}
