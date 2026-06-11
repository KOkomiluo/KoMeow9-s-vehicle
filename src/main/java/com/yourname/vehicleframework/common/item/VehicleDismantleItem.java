package com.yourname.vehicleframework.common.item;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;

import java.util.List;

/**
 * 拆卸扳手 — 右键点击载具将其变回载具生成器掉落物。
 * <p>
 * 实际拆卸逻辑在 VehicleEntity.interact() 中处理，此类仅定义物品属性。
 */
public class VehicleDismantleItem extends Item {

    public VehicleDismantleItem(Properties properties) {
        super(properties.stacksTo(1).durability(64));
    }

    @Override
    public void appendHoverText(ItemStack stack, Level level,
                                List<Component> tooltipComponents, TooltipFlag tooltipFlag) {
        tooltipComponents.add(Component.translatable("item.vehicleframework.dismantle_tool.tooltip")
                .withStyle(ChatFormatting.GOLD));
        tooltipComponents.add(Component.literal(
                (stack.getMaxDamage() - stack.getDamageValue()) + "/" + stack.getMaxDamage() + " uses left")
                .withStyle(ChatFormatting.DARK_GRAY));
    }
}
