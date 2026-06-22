package com.yourname.vehicleframework.client.render;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;

import com.yourname.vehicleframework.VehicleFramework;
import com.yourname.vehicleframework.client.model.ObjFace;
import com.yourname.vehicleframework.client.model.ObjModel;
import com.yourname.vehicleframework.client.model.ObjModelCache;
import com.yourname.vehicleframework.client.model.ObjModelGroup;
import com.yourname.vehicleframework.client.model.ObjVertex;
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
 * 支持两种渲染模式：
 * <ul>
 *   <li><b>有 UV 坐标</b>：使用配置的贴图 + 标准纹理渲染</li>
 *   <li><b>无 UV 坐标</b>：使用 MTL 材质颜色渲染（无贴图，纯色面片）</li>
 * </ul>
 * <p>
 * 按命名子对象独立渲染（仿 MTS RenderableModelObject），为动画预留扩展点。
 */
public class ObjVehicleRenderer extends EntityRenderer<VehicleEntity> {

    private static final float DEFAULT_SCALE = 1.0f / 16.0f;
    private static final ResourceLocation WHITE_TEXTURE =
            new ResourceLocation(VehicleFramework.MOD_ID, "textures/misc/white.png");

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

        // 优先使用同步的 entityData（客户端安全），回退到 VehicleType
        String objPath = entity.getSyncedObjPath();
        if (objPath == null || objPath.isEmpty()) {
            VehicleType config = entity.getVehicleTypeConfig();
            if (config != null) objPath = config.objModelPath();
        }
        if (objPath == null || objPath.isEmpty()) return;

        ObjModelGroup modelGroup = ObjModelCache.get(objPath);
        if (modelGroup == null || modelGroup.getObjects().isEmpty()) return;

        // 检测是否有 UV 坐标（遍历所有子对象中第一个面片）
        boolean hasUVs = modelGroupHasUVs(modelGroup);

        poseStack.pushPose();

        // ── 变换 ──
        float visualYaw = entity.getVisualYaw(partialTick);
        poseStack.mulPose(Axis.YP.rotationDegrees(180.0f - visualYaw));
        poseStack.mulPose(Axis.ZP.rotationDegrees(entity.getVisualBodyRoll(partialTick)));
        poseStack.mulPose(Axis.XP.rotationDegrees(entity.getVisualBodyPitch(partialTick)));

        float scale = entity.getSyncedObjScale();
        if (scale <= 0) {
            VehicleType config = entity.getVehicleTypeConfig();
            scale = (config != null) ? (float) config.objScale() : DEFAULT_SCALE;
        }
        if (scale <= 0) scale = DEFAULT_SCALE;
        poseStack.scale(scale, scale, scale);

        // 居中模型（使用合并包围盒）
        float centerX = (modelGroup.getBoundsMin()[0] + modelGroup.getBoundsMax()[0]) / 2.0f;
        float centerY = modelGroup.getBoundsMin()[1];
        float centerZ = (modelGroup.getBoundsMin()[2] + modelGroup.getBoundsMax()[2]) / 2.0f;
        poseStack.translate(-centerX, -centerY, -centerZ);

        // ── 渲染 ──
        ResourceLocation texture;
        if (hasUVs) {
            texture = getTextureLocation(entity);
        } else {
            texture = WHITE_TEXTURE; // 无UV → 白色基准贴图 + 材质颜色
        }

        VertexConsumer vertexConsumer = bufferSource.getBuffer(
                RenderType.entityCutoutNoCull(texture));

        Matrix4f poseMatrix = poseStack.last().pose();
        Matrix3f normalMatrix = poseStack.last().normal();
        int overlay = OverlayTexture.NO_OVERLAY;

        // 遍历所有命名子对象独立渲染
        for (ObjModel subObject : modelGroup.getObjects().values()) {
            for (ObjFace face : subObject.getFaces()) {
                float r, g, b;
                if (!hasUVs && face.hasColor()) {
                    r = face.color[0]; g = face.color[1]; b = face.color[2];
                } else {
                    r = g = b = 1.0f;
                }
                renderFace(face, vertexConsumer, poseMatrix, normalMatrix,
                        packedLight, overlay, r, g, b);
            }
        }

        poseStack.popPose();
    }

    /** 快速检测模型组中是否有任何有效的 UV 坐标。 */
    private static boolean modelGroupHasUVs(ObjModelGroup group) {
        for (ObjModel obj : group.getObjects().values()) {
            if (obj.getFaces().isEmpty()) continue;
            ObjFace first = obj.getFaces().get(0);
            // 如果 UV 是 (0,0) 且没有材质颜色，说明没有 UV
            if (first.v0.u != 0 || first.v0.v != 0
                    || first.v1.u != 0 || first.v1.v != 0
                    || first.v2.u != 0 || first.v2.v != 0) {
                return true;
            }
        }
        return false;
    }

    private void renderFace(ObjFace face, VertexConsumer consumer,
                            Matrix4f poseMatrix, Matrix3f normalMatrix,
                            int packedLight, int overlay, float r, float g, float b) {
        writeVertex(consumer, poseMatrix, normalMatrix, face.v0, packedLight, overlay, r, g, b);
        writeVertex(consumer, poseMatrix, normalMatrix, face.v1, packedLight, overlay, r, g, b);
        writeVertex(consumer, poseMatrix, normalMatrix, face.v2, packedLight, overlay, r, g, b);
        // Entity RenderTypes consume QUADS. Use a degenerate fourth vertex for
        // each OBJ triangle instead of leaking into the following face.
        writeVertex(consumer, poseMatrix, normalMatrix, face.v2, packedLight, overlay, r, g, b);
    }

    private void writeVertex(VertexConsumer consumer, Matrix4f poseMatrix, Matrix3f normalMatrix,
                             ObjVertex vertex, int packedLight, int overlay,
                             float r, float g, float b) {
        float x = vertex.px, y = vertex.py, z = vertex.pz;
        float tx = poseMatrix.m00() * x + poseMatrix.m01() * y + poseMatrix.m02() * z + poseMatrix.m03();
        float ty = poseMatrix.m10() * x + poseMatrix.m11() * y + poseMatrix.m12() * z + poseMatrix.m13();
        float tz = poseMatrix.m20() * x + poseMatrix.m21() * y + poseMatrix.m22() * z + poseMatrix.m23();

        float nx = vertex.nx, ny = vertex.ny, nz = vertex.nz;
        float tnx = normalMatrix.m00() * nx + normalMatrix.m01() * ny + normalMatrix.m02() * nz;
        float tny = normalMatrix.m10() * nx + normalMatrix.m11() * ny + normalMatrix.m12() * nz;
        float tnz = normalMatrix.m20() * nx + normalMatrix.m21() * ny + normalMatrix.m22() * nz;

        consumer.vertex(tx, ty, tz)
                .color(r, g, b, 1.0f)
                .uv(vertex.u, vertex.v)
                .overlayCoords(overlay)
                .uv2(packedLight)
                .normal(tnx, tny, tnz)
                .endVertex();
    }
}
