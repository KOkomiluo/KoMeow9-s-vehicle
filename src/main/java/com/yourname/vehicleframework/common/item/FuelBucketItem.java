package com.yourname.vehicleframework.common.item;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;

import java.util.List;

public class FuelBucketItem extends Item {

    public static final int CAPACITY_LITERS = 100;

    public FuelBucketItem(Properties properties) {
        super(properties.stacksTo(1).durability(CAPACITY_LITERS));
    }

    public static int getRemainingFuel(ItemStack stack) {
        return Math.max(0, CAPACITY_LITERS - stack.getDamageValue());
    }

    public static void consumeFuel(ItemStack stack, int liters) {
        if (liters <= 0) return;
        int damage = Math.min(CAPACITY_LITERS, stack.getDamageValue() + liters);
        stack.setDamageValue(damage);
    }

    @Override
    public void appendHoverText(ItemStack stack, Level level,
                                List<Component> tooltipComponents, TooltipFlag tooltipFlag) {
        tooltipComponents.add(Component.translatable(
                "item.vehicleframework.fuel_bucket.tooltip",
                getRemainingFuel(stack),
                CAPACITY_LITERS
        ).withStyle(ChatFormatting.GRAY));
    }
}
