package com.yourname.vehicleframework.client.render;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;

import com.yourname.vehicleframework.VehicleFramework;
import com.yourname.vehicleframework.client.model.ObjModel;
import com.yourname.vehicleframework.client.model.ObjModelCache;
import com.yourname.vehicleframework.common.entity.VehicleEntity;
import com.yourname.vehicleframework.data.VehicleType;

import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;

import org.joml.Matrix3f;
import org.joml.Matrix4f;

/**
 * 基于 OBJ 模型的车辆实体渲染器。
 * <p>
 * 从 assets/vehicleframework/models/obj/ 加载 .obj 文件，
 * 自动缩放到 Minecraft 方块单位，并使用对应贴图渲染。
 * <p>
 * 坐标映射：
 * - OBJ 1 单位 = 1/16 Minecraft 方块（可配置缩放）
 * - 模型自动居中到实体位置
 */
public class ObjVehicleRenderer extends EntityRenderer<VehicleEntity> {

    /** 默认缩放：OBJ 1 单位 = 1/16 方块（Blender 默认导出单位） */
    private static final float DEFAULT_SCALE = 1.0f / 16.0f;

    public ObjVehicleRenderer(EntityRendererProvider.Context context) {
        super(context);
        this.shadowRadius = 1.0f;
    }

    @Override
    public ResourceLocation getTextureLocation(VehicleEntity entity) {
        VehicleType config = entity.getVehicleTypeConfig();
        String texture = (config != null && config.texturePath() != null)
                ? config.texturePath() : "sports_car";
        return new ResourceLocation(VehicleFramework.MOD_ID,
                "textures/entity/vehicle/" + texture + ".png");
    }

    @Override
    public void render(VehicleEntity entity, float entityYaw, float partialTick,
                       PoseStack poseStack, MultiBufferSource bufferSource, int packedLight) {
        super.render(entity, entityYaw, partialTick, poseStack, bufferSource, packedLight);

        VehicleType config = entity.getVehicleTypeConfig();
        if (config == null || config.objModelPath() == null || config.objModelPath().isEmpty()) {
            return; // 无 OBJ 模型配置，跳过渲染
        }

        // 加载 OBJ 模型（有缓存）
        ObjModel model = ObjModelCache.get(config.objModelPath());
        if (model == null || model.getFaces().isEmpty()) return;

        poseStack.pushPose();

        // ── 变换 ──
        // 1. 旋转：匹配实体朝向
        poseStack.mulPose(Axis.YP.rotationDegrees(180.0f - entityYaw));

        // 2. 缩放
        float scale = (float) config.objScale();
        if (scale <= 0) scale = DEFAULT_SCALE;
        poseStack.scale(scale, scale, scale);

        // 3. 居中模型（将模型中心对齐到实体位置）
        float centerX = (model.getBoundsMin()[0] + model.getBoundsMax()[0]) / 2.0f;
        float centerY = model.getBoundsMin()[1]; // 底部对齐
        float centerZ = (model.getBoundsMin()[2] + model.getBoundsMax()[2]) / 2.0f;
        poseStack.translate(-centerX, -centerY, -centerZ);

        // ── 渲染 ──
        ResourceLocation texture = getTextureLocation(entity);
        VertexConsumer vertexConsumer = bufferSource.getBuffer(
                RenderType.entityCutoutNoCull(texture));

        Matrix4f poseMatrix = poseStack.last().pose();
        Matrix3f normalMatrix = poseStack.last().normal();

        int overlay = OverlayTexture.NO_OVERLAY;

        for (ObjModel.Face face : model.getFaces()) {
            renderFace(face, vertexConsumer, poseMatrix, normalMatrix, packedLight, overlay);
        }

        poseStack.popPose();
    }

    /**
     * 渲染单个三角面。
     */
    private void renderFace(ObjModel.Face face, VertexConsumer consumer,
                            Matrix4f poseMatrix, Matrix3f normalMatrix,
                            int packedLight, int overlay) {
        writeVertex(consumer, poseMatrix, normalMatrix, face.v0, packedLight, overlay);
        writeVertex(consumer, poseMatrix, normalMatrix, face.v1, packedLight, overlay);
        writeVertex(consumer, poseMatrix, normalMatrix, face.v2, packedLight, overlay);
    }

    /**
     * 写入单个顶点到渲染管线。
     */
    private void writeVertex(VertexConsumer consumer, Matrix4f poseMatrix, Matrix3f normalMatrix,
                             ObjModel.Vertex vertex, int packedLight, int overlay) {
        // 变换位置
        float x = vertex.px, y = vertex.py, z = vertex.pz;
        float tx = poseMatrix.m00() * x + poseMatrix.m01() * y + poseMatrix.m02() * z + poseMatrix.m03();
        float ty = poseMatrix.m10() * x + poseMatrix.m11() * y + poseMatrix.m12() * z + poseMatrix.m13();
        float tz = poseMatrix.m20() * x + poseMatrix.m21() * y + poseMatrix.m22() * z + poseMatrix.m23();

        // 变换法线
        float nx = vertex.nx, ny = vertex.ny, nz = vertex.nz;
        float tnx = normalMatrix.m00() * nx + normalMatrix.m01() * ny + normalMatrix.m02() * nz;
        float tny = normalMatrix.m10() * nx + normalMatrix.m11() * ny + normalMatrix.m12() * nz;
        float tnz = normalMatrix.m20() * nx + normalMatrix.m21() * ny + normalMatrix.m22() * nz;

        consumer.vertex(tx, ty, tz)
                .color(1.0f, 1.0f, 1.0f, 1.0f)
                .uv(vertex.u, vertex.v)
                .overlayCoords(overlay)
                .uv2(packedLight)
                .normal(tnx, tny, tnz)
                .endVertex();
    }
}
