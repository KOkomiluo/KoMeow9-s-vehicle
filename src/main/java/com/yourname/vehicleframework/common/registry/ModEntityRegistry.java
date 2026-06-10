package com.yourname.vehicleframework.common.registry;

import com.yourname.vehicleframework.VehicleFramework;
import com.yourname.vehicleframework.common.entity.VehicleCollisionEntity;
import com.yourname.vehicleframework.common.entity.VehicleEntity;
import com.yourname.vehicleframework.common.entity.VehicleSeatEntity;

import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

public final class ModEntityRegistry {

    private ModEntityRegistry() {}

    public static final DeferredRegister<EntityType<?>> ENTITY_TYPES =
            DeferredRegister.create(ForgeRegistries.ENTITY_TYPES, VehicleFramework.MOD_ID);

    public static final RegistryObject<EntityType<VehicleEntity>> VEHICLE =
            ENTITY_TYPES.register("vehicle", () ->
                    EntityType.Builder.<VehicleEntity>of(VehicleEntity::new, MobCategory.MISC)
                            .sized(2.0f, 1.2f)
                            .clientTrackingRange(10)
                            .updateInterval(1)
                            .build("vehicle"));

    public static final RegistryObject<EntityType<VehicleSeatEntity>> VEHICLE_SEAT =
            ENTITY_TYPES.register("vehicle_seat", () ->
                    EntityType.Builder.<VehicleSeatEntity>of(VehicleSeatEntity::new, MobCategory.MISC)
                            .sized(0.5f, 0.5f)
                            .clientTrackingRange(10)
                            .updateInterval(10)
                            .build("vehicle_seat"));

    public static final RegistryObject<EntityType<VehicleCollisionEntity>> VEHICLE_COLLISION =
            ENTITY_TYPES.register("vehicle_collision", () ->
                    EntityType.Builder.<VehicleCollisionEntity>of(VehicleCollisionEntity::new, MobCategory.MISC)
                            .sized(1.0f, 1.0f)
                            .clientTrackingRange(8)
                            .updateInterval(2)
                            .build("vehicle_collision"));
}
