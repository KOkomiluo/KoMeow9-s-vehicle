package com.yourname.vehicleframework.common.item;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;

import java.util.List;

/**
 * 车辆钥匙物品。
 * <p>
 * Shift + 右键：切换车辆锁定状态。
 * 普通右键：后续实现为选中/绑定车辆功能。
 * <p>
 * 注意：右键点击车辆实体的交互需通过 PlayerInteractEvent 实现，
 * 因为 VehicleEntity 不是 LivingEntity，无法使用 interactLivingEntity。
 */
public class VehicleKeyItem extends Item {

    public VehicleKeyItem(Properties properties) {
        super(properties.stacksTo(1));
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);

        if (!level.isClientSide) {
            if (player.isShiftKeyDown()) {
                // Shift + 右键：切换车辆锁定状态
                player.sendSystemMessage(
                    Component.literal("Vehicle lock toggled.").withStyle(ChatFormatting.YELLOW));
            } else {
                // 普通右键：显示绑定信息
                player.sendSystemMessage(
                    Component.literal("Vehicle key ready. Right-click a vehicle to bind.")
                             .withStyle(ChatFormatting.GREEN));
            }
        }

        return InteractionResultHolder.success(stack);
    }

    @Override
    public void appendHoverText(ItemStack stack, Level level,
                                List<Component> tooltipComponents, TooltipFlag tooltipFlag) {
        tooltipComponents.add(Component.translatable("item.vehicleframework.key.tooltip")
                .withStyle(ChatFormatting.GRAY));
        tooltipComponents.add(Component.literal("Shift+Right-click: Toggle lock")
                .withStyle(ChatFormatting.DARK_GRAY));
    }
}
