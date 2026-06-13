package com.yourname.vehicleframework.client.render;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;

import com.yourname.vehicleframework.VehicleFramework;
import com.yourname.vehicleframework.common.entity.VehicleEntity;

import net.minecraft.client.model.BoatModel;
import net.minecraft.client.model.geom.ModelLayers;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.vehicle.Boat;

import java.lang.reflect.Field;

/**
 * 木船外观渲染器 — 使用 Minecraft 原版 {@link BoatModel} 和橡木船纹理。
 * <p>
 * 当车辆配置未指定 OBJ 模型时，使用此渲染器回退到原版木船外观。
 * 模型从已烘焙的 {@link ModelLayers#createBoatModelName} 层获取。
 * 行驶时自动播放划桨动画，速度越快划桨越快。
 */
public class BoatVehicleRenderer extends EntityRenderer<VehicleEntity> {

    private static final ResourceLocation OAK_BOAT_TEXTURE =
            new ResourceLocation("textures/entity/boat/oak.png");

    private final BoatModel model;
    private final ModelPart leftPaddle;
    private final ModelPart rightPaddle;

    public BoatVehicleRenderer(EntityRendererProvider.Context context) {
        super(context);
        this.shadowRadius = 0.8F;
        this.model = new BoatModel(context.bakeLayer(
                ModelLayers.createBoatModelName(Boat.Type.OAK)));

        // 反射获取船桨部件（BoatModel 字段为 private，setupAnim 又需要 Boat 实体）
        // 尝试多种可能的字段名（Parchment / Mojang / Yarn 命名差异）
        ModelPart left = null;
        ModelPart right = null;
        for (Field f : BoatModel.class.getDeclaredFields()) {
            f.setAccessible(true);
            if (f.getType() == ModelPart.class) {
                try {
                    String name = f.getName();
                    Object val = f.get(this.model);
                    if (val instanceof ModelPart) {
                        VehicleFramework.LOGGER.info("BoatModel ModelPart field: {}", name);
                        if (name.contains("left") || name.contains("Left")) {
                            left = (ModelPart) val;
                        } else if (name.contains("right") || name.contains("Right")) {
                            right = (ModelPart) val;
                        }
                    }
                } catch (Exception ignored) {}
            }
        }
        if (left == null || right == null) {
            VehicleFramework.LOGGER.warn("BoatModel paddle reflection failed — rowing animation disabled");
        }
        this.leftPaddle = left;
        this.rightPaddle = right;
    }

    @Override
    public ResourceLocation getTextureLocation(VehicleEntity entity) {
        return OAK_BOAT_TEXTURE;
    }

    @Override
    public void render(VehicleEntity entity, float entityYaw, float partialTick,
                       PoseStack poseStack, MultiBufferSource bufferSource, int packedLight) {
        poseStack.pushPose();

        // 居中偏移（对齐原版 BoatRenderer）
        poseStack.translate(0.0D, 0.375D, 0.0D);
        poseStack.mulPose(Axis.YP.rotationDegrees(180.0F - entityYaw));

        // 标准船体变换
        poseStack.scale(-1.0F, -1.0F, 1.0F);
        poseStack.mulPose(Axis.YP.rotationDegrees(90.0F));

        // ── 划桨动画（速度相关）──
        float speed = (float) Math.abs(entity.getSpeed());
        if (speed > 0.01f && leftPaddle != null && rightPaddle != null) {
            float phase = (entity.tickCount + partialTick) * speed * 3.5f;
            float paddleRot = (float) Math.sin(phase * Math.PI) * 0.85f;
            leftPaddle.xRot = paddleRot;
            rightPaddle.xRot = -paddleRot;
        } else {
            if (leftPaddle != null) leftPaddle.xRot = 0;
            if (rightPaddle != null) rightPaddle.xRot = 0;
        }

        VertexConsumer vertexConsumer = bufferSource.getBuffer(
                this.model.renderType(getTextureLocation(entity)));
        this.model.renderToBuffer(poseStack, vertexConsumer, packedLight,
                OverlayTexture.NO_OVERLAY, 1.0F, 1.0F, 1.0F, 1.0F);

        poseStack.popPose();
        super.render(entity, entityYaw, partialTick, poseStack, bufferSource, packedLight);
    }
}
