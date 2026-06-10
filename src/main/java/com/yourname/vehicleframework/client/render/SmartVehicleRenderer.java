package com.yourname.vehicleframework.client.render;

import com.mojang.blaze3d.vertex.PoseStack;

import com.yourname.vehicleframework.common.entity.VehicleEntity;
import com.yourname.vehicleframework.data.VehicleType;

import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.resources.ResourceLocation;

/**
 * 智能车辆渲染器 — 根据车辆配置自动选择渲染方式。
 * <p>
 * 渲染策略：
 * <ul>
 *   <li>若 VehicleType.objModelPath 非空 → 使用 {@link ObjVehicleRenderer}（OBJ 静态模型）</li>
 *   <li>若 VehicleType.objModelPath 为空 → 使用 {@link VehicleRenderer}（GeckoLib 骨骼动画）</li>
 * </ul>
 * <p>
 * 每种车辆可以独立选择渲染方式，无需修改代码。
 */
public class SmartVehicleRenderer extends EntityRenderer<VehicleEntity> {

    private final VehicleRenderer geckoRenderer;
    private final ObjVehicleRenderer objRenderer;

    public SmartVehicleRenderer(EntityRendererProvider.Context context) {
        super(context);
        this.geckoRenderer = new VehicleRenderer(context);
        this.objRenderer = new ObjVehicleRenderer(context);
    }

    @Override
    public ResourceLocation getTextureLocation(VehicleEntity entity) {
        // 委托给当前活跃的渲染器
        VehicleType config = entity.getVehicleTypeConfig();
        if (config != null && config.useObjModel()) {
            return objRenderer.getTextureLocation(entity);
        }
        return geckoRenderer.getTextureLocation(entity);
    }

    @Override
    public void render(VehicleEntity entity, float entityYaw, float partialTick,
                       PoseStack poseStack, MultiBufferSource bufferSource, int packedLight) {
        VehicleType config = entity.getVehicleTypeConfig();

        if (config != null && config.useObjModel()) {
            // ── OBJ 模式 ──
            objRenderer.render(entity, entityYaw, partialTick, poseStack, bufferSource, packedLight);
        } else {
            // ── GeckoLib 骨骼动画模式 ──
            geckoRenderer.render(entity, entityYaw, partialTick, poseStack, bufferSource, packedLight);
        }
    }
}
