package com.yourname.vehicleframework.client.render;

import com.yourname.vehicleframework.VehicleFramework;
import com.yourname.vehicleframework.common.entity.VehicleEntity;

import net.minecraft.util.Mth;
import net.minecraft.world.entity.player.Player;

import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RenderPlayerEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

@Mod.EventBusSubscriber(modid = VehicleFramework.MOD_ID, value = Dist.CLIENT)
public final class VehiclePlayerRenderHandler {

    private static final float MAX_HEAD_YAW_FROM_VEHICLE = 90.0F;
    private static final Map<UUID, SavedRotations> SAVED_ROTATIONS = new HashMap<>();

    private VehiclePlayerRenderHandler() {}

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void onRenderPlayerPre(final RenderPlayerEvent.Pre event) {
        Player player = event.getEntity();
        if (!(player.getVehicle() instanceof VehicleEntity vehicle)) return;

        UUID playerId = player.getUUID();
        if (SAVED_ROTATIONS.containsKey(playerId)) return;

        SAVED_ROTATIONS.put(playerId, SavedRotations.capture(player));

        float bodyYaw = vehicle.getVisualYaw(event.getPartialTick());
        float headYawOffset = Mth.clamp(
                Mth.wrapDegrees(player.getYRot() - bodyYaw),
                -MAX_HEAD_YAW_FROM_VEHICLE,
                MAX_HEAD_YAW_FROM_VEHICLE);
        float headYaw = bodyYaw + headYawOffset;

        player.setYRot(bodyYaw);
        player.yRotO = bodyYaw;
        player.yBodyRot = bodyYaw;
        player.yBodyRotO = bodyYaw;
        player.setYHeadRot(headYaw);
        player.yHeadRotO = headYaw;
    }

    @SubscribeEvent
    public static void onRenderPlayerPost(final RenderPlayerEvent.Post event) {
        SavedRotations rotations = SAVED_ROTATIONS.remove(event.getEntity().getUUID());
        if (rotations != null) {
            rotations.restore(event.getEntity());
        }
    }

    private record SavedRotations(
            float yRot,
            float yRotO,
            float xRot,
            float xRotO,
            float yBodyRot,
            float yBodyRotO,
            float yHeadRot,
            float yHeadRotO) {

        static SavedRotations capture(Player player) {
            return new SavedRotations(
                    player.getYRot(),
                    player.yRotO,
                    player.getXRot(),
                    player.xRotO,
                    player.yBodyRot,
                    player.yBodyRotO,
                    player.yHeadRot,
                    player.yHeadRotO);
        }

        void restore(Player player) {
            player.setYRot(yRot);
            player.yRotO = yRotO;
            player.setXRot(xRot);
            player.xRotO = xRotO;
            player.yBodyRot = yBodyRot;
            player.yBodyRotO = yBodyRotO;
            player.setYHeadRot(yHeadRot);
            player.yHeadRotO = yHeadRotO;
        }
    }
}
