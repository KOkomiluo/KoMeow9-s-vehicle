package com.yourname.vehicleframework.client.render;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;

import com.yourname.vehicleframework.VehicleFramework;
import com.yourname.vehicleframework.client.model.ObjFace;
import com.yourname.vehicleframework.client.model.ObjModel;
import com.yourname.vehicleframework.client.model.ObjModelCache;
import com.yourname.vehicleframework.common.entity.VehicleEntity;

import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;

import org.joml.Matrix3f;
import org.joml.Matrix4f;

public class VehicleRenderer extends EntityRenderer<VehicleEntity> {

    private static final ResourceLocation MODEL_LOCATION =
            new ResourceLocation(VehicleFramework.MOD_ID, "models/obj/civic2008.obj");
    private static final ResourceLocation TEXTURE_LOCATION =
            new ResourceLocation(VehicleFramework.MOD_ID, "textures/entity/vehicle/civic2008.png");
    private static final float SCALE = 1.0f;

    private static ObjModel model = null;

    public VehicleRenderer(EntityRendererProvider.Context context) {
        super(context);
        this.shadowRadius = 1.0f;
        // 预加载贴图：触发 TextureManager 注册
        // 不调用此行则 entitySolid 渲染时拿到未初始化的 GL 纹理对象 → 彩色花块
        Minecraft.getInstance().getTextureManager()
                 .getTexture(TEXTURE_LOCATION);
    }

    @Override
    public ResourceLocation getTextureLocation(VehicleEntity entity) {
        return TEXTURE_LOCATION;
    }

    @Override
    public void render(VehicleEntity entity, float entityYaw, float partialTick,
                       PoseStack poseStack, MultiBufferSource bufferSource, int packedLight) {
        if (model == null) model = ObjModelCache.get(MODEL_LOCATION);
        if (model == null || model.getFaces().isEmpty()) return;

        poseStack.pushPose();

        float centerX = (model.getBoundsMin()[0] + model.getBoundsMax()[0]) / 2.0f;
        float centerY =  model.getBoundsMin()[1];
        float centerZ = (model.getBoundsMin()[2] + model.getBoundsMax()[2]) / 2.0f;

        // PoseStack 是逆序矩阵栈，调用顺序与执行顺序相反
        // 执行顺序（模型空间 → 世界空间）：
        //   1. translate：模型居中到原点
        //   2. scale：缩放
        //   3. rotate：绕 Y 轴旋转对齐实体朝向
        // 因此调用顺序反过来写：
        poseStack.mulPose(Axis.YP.rotationDegrees(-entityYaw)); // 执行第 3 步
        poseStack.scale(SCALE, SCALE, SCALE);                   // 执行第 2 步
        poseStack.translate(-centerX, -centerY, -centerZ);      // 执行第 1 步

        VertexConsumer vc = bufferSource.getBuffer(RenderType.entitySolid(TEXTURE_LOCATION));
        Matrix4f pm = poseStack.last().pose();
        Matrix3f nm = poseStack.last().normal();

        for (ObjFace face : model.getFaces()) {
            putVertex(vc, pm, nm, face.v0, packedLight);
            putVertex(vc, pm, nm, face.v1, packedLight);
            putVertex(vc, pm, nm, face.v2, packedLight);
        }

        poseStack.popPose();
        super.render(entity, entityYaw, partialTick, poseStack, bufferSource, packedLight);
    }

    private static void putVertex(VertexConsumer vc, Matrix4f pm, Matrix3f nm,
                                  com.yourname.vehicleframework.client.model.ObjVertex v,
                                  int light) {
        vc.vertex(pm, v.px, v.py, v.pz)
          .color(1.0f, 1.0f, 1.0f, 1.0f)
          .uv(v.u, v.v)
          .overlayCoords(OverlayTexture.NO_OVERLAY)
          .uv2(light)
          .normal(nm, v.nx, v.ny, v.nz)
          .endVertex();
    }
}
