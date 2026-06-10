package com.yourname.vehicleframework.common.registry;

import com.yourname.vehicleframework.VehicleFramework;
import com.yourname.vehicleframework.common.item.VehicleKeyItem;
import com.yourname.vehicleframework.common.item.VehicleSpawnItem;

import net.minecraft.world.item.Item;
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
}
