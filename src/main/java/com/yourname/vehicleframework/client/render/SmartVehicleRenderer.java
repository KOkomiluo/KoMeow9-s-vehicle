package com.yourname.vehicleframework.client.render;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;

import com.yourname.vehicleframework.VehicleFramework;
import com.yourname.vehicleframework.client.model.ObjModel;
import com.yourname.vehicleframework.client.model.ObjModelCache;
import com.yourname.vehicleframework.common.entity.VehicleEntity;

import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;

import org.joml.Matrix3f;
import org.joml.Matrix4f;

/**
 * 车辆 OBJ 模型渲染器 — 直接加载 Blockbench 导出的 OBJ 模型。
 * <p>
 * 重要：MC 1.20.1 NeoForge 的 EntityRenderer.render() 接收到的 poseStack 平移量为零。
 * 需要手动计算实体插值位置到相机的偏移并自行平移。
 */
public class SmartVehicleRenderer extends EntityRenderer<VehicleEntity> {

    private static final String OBJ_PATH = "models/obj/civic2008.obj";
    private static final ResourceLocation TEXTURE =
            new ResourceLocation(VehicleFramework.MOD_ID, "textures/entity/vehicle/civic2008.png");
    private static final float SCALE = 0.5f;

    private static boolean DEBUG_LOGGED = false;

    public SmartVehicleRenderer(EntityRendererProvider.Context context) {
        super(context);
        this.shadowRadius = 1.0f;
    }

    @Override
    public ResourceLocation getTextureLocation(VehicleEntity entity) {
        return TEXTURE;
    }

    @Override
    public void render(VehicleEntity entity, float entityYaw, float partialTick,
                       PoseStack poseStack, MultiBufferSource bufferSource, int packedLight) {
        ObjModel model = ObjModelCache.get(OBJ_PATH);
        if (model == null || model.getFaces().isEmpty()) {
            if (!DEBUG_LOGGED) {
                VehicleFramework.LOGGER.error("OBJ not loaded: {}", OBJ_PATH);
                DEBUG_LOGGED = true;
            }
            return;
        }

        // 诊断：仅首次渲染时输出
        if (!DEBUG_LOGGED) {
            VehicleFramework.LOGGER.info("=== OBJ Render ===");
            VehicleFramework.LOGGER.info("  Model Y bounds: {}..{}  Faces: {}  Scale: {}",
                    model.getBoundsMin()[1], model.getBoundsMax()[1],
                    model.getFaces().size(), SCALE);
            VehicleFramework.LOGGER.info("  Entity: ({}, {}, {})",
                    entity.getX(), entity.getY(), entity.getZ());
            Player p = Minecraft.getInstance().player;
            if (p != null) {
                VehicleFramework.LOGGER.info("  Player: ({}, {}, {}) eyeH={}",
                        p.getX(), p.getY(), p.getZ(), p.getEyeHeight());
            }
            DEBUG_LOGGED = true;
        }

        // ── 修复：poseStack 在 1.20.1 NeoForge 中没有被 EntityRenderDispatcher 平移 ──
        // 手动计算实体插值世界位置 → 相机相对位置 → 自行平移
        Camera camera = Minecraft.getInstance().gameRenderer.getMainCamera();
        Vec3 camPos = camera.getPosition();
        double ix = Mth.lerp(partialTick, entity.xOld, entity.getX());
        double iy = Mth.lerp(partialTick, entity.yOld, entity.getY());
        double iz = Mth.lerp(partialTick, entity.zOld, entity.getZ());

        poseStack.pushPose();
        poseStack.translate(ix - camPos.x, iy - camPos.y, iz - camPos.z);  // 移到实体世界位置

        // 旋转到实体朝向
        poseStack.mulPose(Axis.YP.rotationDegrees(180.0f - entityYaw));
        poseStack.scale(SCALE, SCALE, SCALE);

        // 居中模型：XZ 中心对齐，Y 底部对齐
        float centerX = (model.getBoundsMin()[0] + model.getBoundsMax()[0]) / 2.0f;
        float centerY = model.getBoundsMin()[1];
        float centerZ = (model.getBoundsMin()[2] + model.getBoundsMax()[2]) / 2.0f;
        poseStack.translate(-centerX, -centerY, -centerZ);

        VertexConsumer vc = bufferSource.getBuffer(RenderType.entitySolid(TEXTURE));
        Matrix4f pm = poseStack.last().pose();
        Matrix3f nm = poseStack.last().normal();

        for (ObjModel.Face face : model.getFaces()) {
            renderFace(face, vc, pm, nm, packedLight);
        }

        poseStack.popPose();
        super.render(entity, entityYaw, partialTick, poseStack, bufferSource, packedLight);
    }

    private static void renderFace(ObjModel.Face face, VertexConsumer vc,
                                   Matrix4f pm, Matrix3f nm, int light) {
        writeVertex(face.v0, vc, pm, nm, light);
        writeVertex(face.v1, vc, pm, nm, light);
        writeVertex(face.v2, vc, pm, nm, light);
    }

    private static void writeVertex(ObjModel.Vertex v, VertexConsumer vc,
                                    Matrix4f pm, Matrix3f nm, int light) {
        vc.vertex(
                pm.m00() * v.px + pm.m01() * v.py + pm.m02() * v.pz + pm.m03(),
                pm.m10() * v.px + pm.m11() * v.py + pm.m12() * v.pz + pm.m13(),
                pm.m20() * v.px + pm.m21() * v.py + pm.m22() * v.pz + pm.m23()
        ).color(1.0f, 1.0f, 1.0f, 1.0f)
         .uv(v.u, v.v)
         .overlayCoords(OverlayTexture.NO_OVERLAY)
         .uv2(light)
         .normal(
                nm.m00() * v.nx + nm.m01() * v.ny + nm.m02() * v.nz,
                nm.m10() * v.nx + nm.m11() * v.ny + nm.m12() * v.nz,
                nm.m20() * v.nx + nm.m21() * v.ny + nm.m22() * v.nz
         ).endVertex();
    }
}
