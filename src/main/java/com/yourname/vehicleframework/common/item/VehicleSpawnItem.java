package com.yourname.vehicleframework.common.item;

import com.yourname.vehicleframework.common.entity.VehicleEntity;
import com.yourname.vehicleframework.common.registry.ModEntityRegistry;
import com.yourname.vehicleframework.data.VehicleConfigLoader;
import com.yourname.vehicleframework.data.VehicleType;

import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;

import java.util.List;

/**
 * 万能车辆生成器物品（创造模式工具）。
 * <p>
 * 右键点击地面在指定位置生成对应类型的车辆实体。
 */
public class VehicleSpawnItem extends Item {

    public VehicleSpawnItem(Properties properties) {
        super(properties.stacksTo(1));
    }

    @Override
    public InteractionResult useOn(UseOnContext context) {
        Level level = context.getLevel();
        BlockPos pos = context.getClickedPos();

        if (!level.isClientSide) {
            String typeKey = "sports_car"; // TODO: 从 NBT 或 GUI 选择

            VehicleEntity vehicle = new VehicleEntity(
                    ModEntityRegistry.VEHICLE.get(), level);
            vehicle.setPos(pos.getX() + 0.5, pos.getY() + 1.0, pos.getZ() + 0.5);
            vehicle.setYRot(context.getPlayer().getYRot());

            VehicleType type = VehicleConfigLoader.getConfig(typeKey);
            if (type != null) {
                vehicle.setVehicleType(type);
            }

            level.addFreshEntity(vehicle);

            if (context.getPlayer() != null) {
                context.getPlayer().sendSystemMessage(
                    Component.literal("Spawned vehicle: " + typeKey)
                             .withStyle(ChatFormatting.GREEN));
            }

            if (context.getPlayer() == null || !context.getPlayer().getAbilities().instabuild) {
                context.getItemInHand().shrink(1);
            }
        }
        return InteractionResult.SUCCESS;
    }

    @Override
    public void appendHoverText(ItemStack stack, Level level,
                                List<Component> tooltipComponents, TooltipFlag tooltipFlag) {
        tooltipComponents.add(Component.translatable("item.vehicleframework.spawn_item.tooltip")
                .withStyle(ChatFormatting.AQUA));
    }

    @Override
    public boolean isFoil(ItemStack stack) {
        return true;
    }
}
