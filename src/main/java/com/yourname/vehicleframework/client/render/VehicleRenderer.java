package com.yourname.vehicleframework.client.render;

import com.yourname.vehicleframework.VehicleFramework;
import com.yourname.vehicleframework.common.entity.VehicleEntity;

import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.resources.ResourceLocation;

import software.bernie.geckolib.renderer.GeoEntityRenderer;

/**
 * 车辆实体渲染器。
 * <p>
 * 基于 GeckoLib GeoEntityRenderer，加载 .geo.json 模型、贴图和动画文件。
 */
public class VehicleRenderer extends GeoEntityRenderer<VehicleEntity> {

    public VehicleRenderer(EntityRendererProvider.Context renderManager) {
        super(renderManager, new VehicleModel());
    }

    @Override
    public ResourceLocation getTextureLocation(VehicleEntity animatable) {
        var config = animatable.getVehicleTypeConfig();
        String textureName = (config != null && config.texturePath() != null)
                ? config.texturePath() : "sports_car";
        return new ResourceLocation(
                VehicleFramework.MOD_ID, "textures/entity/vehicle/" + textureName + ".png");
    }
}
