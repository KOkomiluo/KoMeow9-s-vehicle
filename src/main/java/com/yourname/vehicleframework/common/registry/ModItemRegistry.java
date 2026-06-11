package com.yourname.vehicleframework.common.registry;

import com.yourname.vehicleframework.VehicleFramework;
import com.yourname.vehicleframework.common.item.VehicleDismantleItem;
import com.yourname.vehicleframework.common.item.VehicleKeyItem;
import com.yourname.vehicleframework.common.item.VehicleSpawnItem;

import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

public final class ModItemRegistry {

    private ModItemRegistry() {}

    public static final DeferredRegister<Item> ITEMS =
            DeferredRegister.create(ForgeRegistries.ITEMS, VehicleFramework.MOD_ID);

    public static final RegistryObject<VehicleKeyItem> VEHICLE_KEY =
            ITEMS.register("vehicle_key", () ->
                    new VehicleKeyItem(new Item.Properties()));

    public static final RegistryObject<VehicleSpawnItem> VEHICLE_SPAWN_ITEM =
            ITEMS.register("vehicle_spawn_item", () ->
                    new VehicleSpawnItem(new Item.Properties()));

    public static final RegistryObject<Item> FUEL_BUCKET =
            ITEMS.register("fuel_bucket", () ->
                    new Item(new Item.Properties().stacksTo(16)));

    public static final RegistryObject<VehicleDismantleItem> DISMANTLE_TOOL =
            ITEMS.register("dismantle_tool", () ->
                    new VehicleDismantleItem(new Item.Properties()));

    /** 创建一个指定车型的载具生成器 ItemStack。 */
    public static ItemStack createVehicleSpawnStack(String typeKey) {
        ItemStack stack = new ItemStack(VEHICLE_SPAWN_ITEM.get());
        VehicleSpawnItem.setVehicleType(stack, typeKey);
        return stack;
    }
}
