package com.yourname.vehicleframework.client.render;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;

import com.yourname.vehicleframework.VehicleFramework;
import com.yourname.vehicleframework.client.model.ObjFace;
import com.yourname.vehicleframework.client.model.ObjModel;
import com.yourname.vehicleframework.client.model.ObjModelCache;
import com.yourname.vehicleframework.client.model.ObjModelGroup;
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
            new ResourceLocation(VehicleFramework.MOD_ID, "models/obj/civic_2008.obj");
    /** 模型贴图。 */
    private static final ResourceLocation TEXTURE_LOCATION =
            new ResourceLocation(VehicleFramework.MOD_ID, "textures/entity/vehicle/civic_2008.png");
    private static final float SCALE = 1.0f;

    private static ObjModelGroup modelGroup = null;

    /**
     * 模型 Y 轴旋转偏移（度）。
     * <p>
     * 调整以匹配建模软件中模型的朝向：
     * 0 = 模型前脸朝南（MC 默认实体朝向），
     * 180 = 模型前脸朝北（Blockbench 常见）。
     * 值不对会导致方向盘/驾驶座左右镜像。
     */
    private static final float YAW_OFFSET = 0.0f;

    public VehicleRenderer(EntityRendererProvider.Context context) {
        super(context);
        this.shadowRadius = 1.0f;
        Minecraft.getInstance().getTextureManager().getTexture(TEXTURE_LOCATION);
    }

    @Override
    public ResourceLocation getTextureLocation(VehicleEntity entity) {
        return TEXTURE_LOCATION;
    }

    @Override
    public void render(VehicleEntity entity, float entityYaw, float partialTick,
                       PoseStack poseStack, MultiBufferSource bufferSource, int packedLight) {
        if (modelGroup == null) modelGroup = ObjModelCache.get(MODEL_LOCATION);
        if (modelGroup == null || modelGroup.getObjects().isEmpty()) return;

        poseStack.pushPose();

        // 使用合并包围盒居中整个模型
        float centerX = (modelGroup.getBoundsMin()[0] + modelGroup.getBoundsMax()[0]) / 2.0f;
        float centerY =  modelGroup.getBoundsMin()[1];
        float centerZ = (modelGroup.getBoundsMin()[2] + modelGroup.getBoundsMax()[2]) / 2.0f;

        poseStack.mulPose(Axis.YP.rotationDegrees(YAW_OFFSET - entityYaw));
        poseStack.scale(SCALE, SCALE, SCALE);
        poseStack.translate(-centerX, -centerY, -centerZ);

        // The atlas is transparent outside its UV islands. Cutout preserves those
        // holes and writes depth, preventing interior faces from showing through.
        VertexConsumer vc = bufferSource.getBuffer(RenderType.entityCutoutNoCull(TEXTURE_LOCATION));
        Matrix4f pm = poseStack.last().pose();
        Matrix3f nm = poseStack.last().normal();

        // 遍历所有命名子对象，白色顶点颜色 × 贴图 = 真实贴图渲染
        for (ObjModel subObject : modelGroup.getObjects().values()) {
            for (ObjFace face : subObject.getFaces()) {
                putVertex(vc, pm, nm, face.v0, packedLight);
                putVertex(vc, pm, nm, face.v1, packedLight);
                putVertex(vc, pm, nm, face.v2, packedLight);
                // Entity RenderTypes consume QUADS. Repeat the final triangle vertex
                // so adjacent OBJ faces are not incorrectly joined into stray geometry.
                putVertex(vc, pm, nm, face.v2, packedLight);
            }
        }

        poseStack.popPose();
        super.render(entity, entityYaw, partialTick, poseStack, bufferSource, packedLight);
    }

    private static void putVertex(VertexConsumer vc, Matrix4f pm, Matrix3f nm,
                                  com.yourname.vehicleframework.client.model.ObjVertex v,
                                  int light) {
        // 用顶点法线恢复 3D 立体光影（有贴图后，纹理细节掩盖 smooth normal 梯度）
        vc.vertex(pm, v.px, v.py, v.pz)
          .color(1.0f, 1.0f, 1.0f, 1.0f)
          .uv(v.u, v.v)
          .overlayCoords(OverlayTexture.NO_OVERLAY)
          .uv2(light)
          .normal(nm, v.nx, v.ny, v.nz)
          .endVertex();
    }
}
