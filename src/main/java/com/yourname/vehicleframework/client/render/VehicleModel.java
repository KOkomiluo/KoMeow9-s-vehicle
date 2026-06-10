package com.yourname.vehicleframework.client.render;

import com.yourname.vehicleframework.VehicleFramework;
import com.yourname.vehicleframework.common.entity.VehicleEntity;

import net.minecraft.resources.ResourceLocation;

import software.bernie.geckolib.model.DefaultedEntityGeoModel;

/**
 * 车辆 GeckoLib 模型定义。
 * <p>
 * 关联 .geo.json 骨骼模型、动画文件和贴图路径。
 */
public class VehicleModel extends DefaultedEntityGeoModel<VehicleEntity> {

    public VehicleModel() {
        super(new ResourceLocation(VehicleFramework.MOD_ID, "vehicle"));
    }

    // 可在此添加自定义动画逻辑（如根据转向角旋转前轮骨骼）
}
