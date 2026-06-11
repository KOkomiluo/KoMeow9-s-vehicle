package com.yourname.vehicleframework.common.item;

import com.yourname.vehicleframework.common.entity.VehicleEntity;
import com.yourname.vehicleframework.common.registry.ModEntityRegistry;
import com.yourname.vehicleframework.data.VehicleConfigLoader;
import com.yourname.vehicleframework.data.VehicleType;

import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
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
 * 支持 NBT 存储车辆类型，可为每个生成器独立指定车型。
 */
public class VehicleSpawnItem extends Item {

    private static final String TAG_VEHICLE_TYPE = "VehicleType";

    public VehicleSpawnItem(Properties properties) {
        super(properties.stacksTo(1));
    }

    // ── NBT 工具方法 ────────────────────────────────────────────────────────

    /** 设置 ItemStack 对应的车辆类型。 */
    public static void setVehicleType(ItemStack stack, String typeKey) {
        CompoundTag tag = stack.getOrCreateTag();
        tag.putString(TAG_VEHICLE_TYPE, typeKey);
    }

    /** 从 ItemStack 读取车辆类型 key，无数据时返回默认 "sports_car"。 */
    public static String getVehicleType(ItemStack stack) {
        CompoundTag tag = stack.getTag();
        if (tag != null && tag.contains(TAG_VEHICLE_TYPE)) {
            return tag.getString(TAG_VEHICLE_TYPE);
        }
        return "sports_car";
    }

    // ── 使用逻辑 ────────────────────────────────────────────────────────────

    @Override
    public InteractionResult useOn(UseOnContext context) {
        Level level = context.getLevel();
        BlockPos pos = context.getClickedPos();

        if (!level.isClientSide) {
            String typeKey = getVehicleType(context.getItemInHand());

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
        String typeKey = getVehicleType(stack);
        tooltipComponents.add(Component.translatable("item.vehicleframework.vehicle_spawn_item.tooltip")
                .withStyle(ChatFormatting.AQUA));
        tooltipComponents.add(Component.literal("Type: " + typeKey)
                .withStyle(ChatFormatting.GRAY));
    }

    @Override
    public Component getName(ItemStack stack) {
        String typeKey = getVehicleType(stack);
        VehicleType config = VehicleConfigLoader.getConfig(typeKey);
        String vehicleName = (config != null) ? config.displayName() : typeKey;
        return Component.translatable(this.getDescriptionId(stack))
                .append(" (" + vehicleName + ")");
    }

    @Override
    public boolean isFoil(ItemStack stack) {
        return true;
    }
}
