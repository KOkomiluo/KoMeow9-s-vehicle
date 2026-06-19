package com.yourname.vehicleframework.client.model;

import com.yourname.vehicleframework.VehicleFramework;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.Resource;
import net.minecraft.server.packs.resources.ResourceManager;

import org.slf4j.Logger;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * OBJ 模型加载器（仿 MTS 做法：按对象名拆分）。
 * <p>
 * 解析 Wavefront OBJ 文件，按 "o name" 指令将模型拆分为独立的子对象。
 * 每个子对象拥有独立的面片列表和包围盒，方便后续实现车轮旋转、车门开闭等动画。
 * <p>
 * 参考 MTS {@code ModelParserOBJ} 的解析架构：
 * - 全局共享顶点/法线/UV 缓冲区（OBJ 索引是全局的）
 * - 按 "o" 指令拆分对象边界
 * - 四边形→三角形扇面三角化
 * - 若无 "o" 指令，所有面归入默认名 "model"
 * <p>
 * <b>坐标轴翻转开关</b>：调整以下常量以匹配建模软件的导出坐标系。
 * 值 =  1.0f → 保持原样（MTS 默认做法）
 * 值 = -1.0f → 翻转该轴（取反）
 * 例如：方向盘左右反了 → 试试 {@code FLIP_X = -1.0f}
 *       车头车尾反了 → 试试 {@code FLIP_Z = -1.0f}
 */
public final class ObjLoader {

    private ObjLoader() {}

    private static final Logger LOGGER = VehicleFramework.LOGGER;
    private static final String DEFAULT_OBJECT_NAME = "model";

    // ── 坐标轴翻转（控制模型朝向，不影响绕序）──
    // 绕序由 per-face 法线一致性检查自动修复，不再用全局标志
    /** X 轴翻转：-1.0f 可修复模型左右镜像问题（方向盘左右反了）。 */
    private static final float FLIP_X =  1.0f;
    /** Y 轴翻转：通常不需要改。 */
    private static final float FLIP_Y =  1.0f;
    /** Z 轴翻转：OBJ 右手系→MC 左手系。MTS 不翻，若模型前后/左右反了设 -1.0f。 */
    private static final float FLIP_Z = -1.0f;

    // ── 公共入口 ──

    /**
     * 从资源位置加载 OBJ 文件，返回按命名对象拆分的模型组。
     *
     * @param resourceLocation OBJ 文件资源位置
     * @return 模型组（name → ObjModel），加载失败返回 null
     */
    public static ObjModelGroup load(ResourceLocation resourceLocation) {
        try {
            ResourceManager rm = net.minecraft.client.Minecraft.getInstance().getResourceManager();
            Optional<Resource> resource = rm.getResource(resourceLocation);
            if (resource.isEmpty()) {
                LOGGER.error("OBJ model not found: {}", resourceLocation);
                return null;
            }
            try (InputStream stream = resource.get().open()) {
                return parse(stream, resourceLocation);
            }
        } catch (Exception e) {
            LOGGER.error("Failed to load OBJ model: {}", resourceLocation, e);
            return null;
        }
    }

    // ── 解析主循环 ──

    /**
     * 解析 OBJ 输入流。
     * <p>
     * 架构仿 MTS：全局顶点缓冲区 + 按对象名收集面数据。
     * 遇到 "o name" 时，将当前对象的面片编译为 {@link ObjModel} 并存档。
     */
    public static ObjModelGroup parse(InputStream inputStream, ResourceLocation objLocation)
            throws Exception {

        // ── 全局共享缓冲区（OBJ 索引是跨对象的）──
        List<float[]> positions = new ArrayList<>();
        List<float[]> normals   = new ArrayList<>();
        List<float[]> texCoords = new ArrayList<>();

        // ── 当前正在收集的对象 ──
        String currentObjectName = DEFAULT_OBJECT_NAME;
        List<ObjFace> currentFaces = new ArrayList<>();

        // ── 结果集（LinkedHashMap 保持插入顺序，与 MTS 的 List<RenderableVertices> 同理）──
        Map<String, ObjModel> objectModels = new LinkedHashMap<>();

        // ── 合并包围盒（所有顶点的全局范围，用于整个模型的居中）──
        float minX =  Float.MAX_VALUE, minY =  Float.MAX_VALUE, minZ =  Float.MAX_VALUE;
        float maxX = -Float.MAX_VALUE, maxY = -Float.MAX_VALUE, maxZ = -Float.MAX_VALUE;

        // ── 材质系统 ──
        Map<String, float[]> materialColors = new HashMap<>();
        String currentMaterial = null;

        int totalFaceCount = 0;

        BufferedReader reader = new BufferedReader(
                new InputStreamReader(inputStream, StandardCharsets.UTF_8));
        String line;

        while ((line = reader.readLine()) != null) {
            line = line.trim();
            if (line.isEmpty() || line.startsWith("#")) continue;

            String[] parts   = line.split("\\s+");
            String   keyword = parts[0];

            switch (keyword) {
                // ── 对象/分组边界（Blockbench 用 g，Blender 用 o，两者都支持）──
                case "o":
                case "g":
                    if (parts.length >= 2) {
                        // 保存当前对象
                        flushCurrentObject(currentObjectName, currentFaces, objectModels);
                        currentObjectName = parts[1];
                        currentFaces = new ArrayList<>();
                    }
                    break;

                // ── MTL 材质引用 ──
                case "mtllib":
                    if (parts.length >= 2) {
                        String mtlPath = resolveMtlPath(objLocation, parts[1]);
                        materialColors = loadMaterialColors(mtlPath);
                        LOGGER.info("MTL loaded: {} materials from {}", materialColors.size(), mtlPath);
                    }
                    break;

                // ── 材质切换 ──
                case "usemtl":
                    if (parts.length >= 2) currentMaterial = parts[1];
                    break;

                // ── 顶点位置 ──
                case "v":
                    if (parts.length >= 4) {
                        float x = FLIP_X * Float.parseFloat(parts[1]);
                        float y = FLIP_Y * Float.parseFloat(parts[2]);
                        float z = FLIP_Z * Float.parseFloat(parts[3]);
                        positions.add(new float[]{x, y, z});
                        minX = Math.min(minX, x); maxX = Math.max(maxX, x);
                        minY = Math.min(minY, y); maxY = Math.max(maxY, y);
                        minZ = Math.min(minZ, z); maxZ = Math.max(maxZ, z);
                    }
                    break;

                // ── 法线 ──
                case "vn":
                    if (parts.length >= 4) {
                        float nx = FLIP_X * Float.parseFloat(parts[1]);
                        float ny = FLIP_Y * Float.parseFloat(parts[2]);
                        float nz = FLIP_Z * Float.parseFloat(parts[3]);
                        normals.add(new float[]{nx, ny, nz});
                    }
                    break;

                // ── UV（不 clamp，只翻 V）──
                case "vt":
                    if (parts.length >= 3) {
                        float u =        Float.parseFloat(parts[1]);
                        float v = 1.0f - Float.parseFloat(parts[2]); // V 翻转：左上原点→左下原点
                        texCoords.add(new float[]{u, v});
                    }
                    break;

                // ── 面 ──
                case "f":
                    float[] matColor = materialColors.get(currentMaterial);
                    int beforeSize = currentFaces.size();
                    parseFaceTo(parts, positions, texCoords, normals, matColor, currentFaces);
                    totalFaceCount += (currentFaces.size() - beforeSize);
                    break;
            }
        }

        // ── 保存最后一个对象 ──
        flushCurrentObject(currentObjectName, currentFaces, objectModels);

        // ── Debug 日志（计数 + 样本面）──
        LOGGER.info("OBJ parsed from {}: {} vertices, {} uvs, {} normals, {} faces, {} objects",
                objLocation, positions.size(), texCoords.size(), normals.size(),
                totalFaceCount, objectModels.size());
        if (!objectModels.isEmpty()) {
            ObjModel first = objectModels.values().iterator().next();
            if (!first.getFaces().isEmpty()) {
                ObjFace f = first.getFaces().get(0);
                LOGGER.info("Sample face [{}]: v0 pos=({},{},{}) uv=({},{}) normal=({},{},{})",
                        first.getName(),
                        f.v0.px, f.v0.py, f.v0.pz,
                        f.v0.u, f.v0.v,
                        f.v0.nx, f.v0.ny, f.v0.nz);
            }
        }

        float[] boundsMin = {minX, minY, minZ};
        float[] boundsMax = {maxX, maxY, maxZ};
        return new ObjModelGroup(objectModels, boundsMin, boundsMax);
    }

    // ── 对象编译 ──

    /**
     * 将当前收集的面片列表编译为一个 {@link ObjModel}，并存入 objects 映射。
     * <p>
     * 仿 MTS 的 {@code compileVertexArray()}：对象名不存在时使用默认名，
     * 遇到空面列表则跳过（不产生空对象）。
     */
    private static void flushCurrentObject(String objectName,
                                           List<ObjFace> faces,
                                           Map<String, ObjModel> objects) {
        if (objectName == null || faces.isEmpty()) return;

        // 防止同名对象覆盖：追加序号
        String uniqueName = objectName;
        int suffix = 1;
        while (objects.containsKey(uniqueName)) {
            uniqueName = objectName + "_" + (suffix++);
        }

        // 计算当前对象的包围盒
        float minX =  Float.MAX_VALUE, minY =  Float.MAX_VALUE, minZ =  Float.MAX_VALUE;
        float maxX = -Float.MAX_VALUE, maxY = -Float.MAX_VALUE, maxZ = -Float.MAX_VALUE;

        for (ObjFace face : faces) {
            for (ObjVertex v : new ObjVertex[]{face.v0, face.v1, face.v2}) {
                minX = Math.min(minX, v.px); maxX = Math.max(maxX, v.px);
                minY = Math.min(minY, v.py); maxY = Math.max(maxY, v.py);
                minZ = Math.min(minZ, v.pz); maxZ = Math.max(maxZ, v.pz);
            }
        }

        float[] boundsMin = {minX, minY, minZ};
        float[] boundsMax = {maxX, maxY, maxZ};

        objects.put(uniqueName, new ObjModel(objectName, faces, boundsMin, boundsMax));
        LOGGER.debug("  OBJ object '{}': {} faces", uniqueName, faces.size());
    }

    // ── MTL 材质加载 ──

    private static Map<String, float[]> loadMaterialColors(String mtlPath) {
        Map<String, float[]> colors = new HashMap<>();
        try {
            ResourceManager rm = net.minecraft.client.Minecraft.getInstance().getResourceManager();
            ResourceLocation mtlLoc = new ResourceLocation(VehicleFramework.MOD_ID, mtlPath);
            Optional<Resource> res = rm.getResource(mtlLoc);
            if (res.isEmpty()) {
                LOGGER.warn("MTL file not found: {}", mtlLoc);
                return colors;
            }
            try (InputStream stream = res.get().open();
                 BufferedReader reader = new BufferedReader(
                         new InputStreamReader(stream, StandardCharsets.UTF_8))) {
                String currentMtl = null;
                String line;
                while ((line = reader.readLine()) != null) {
                    line = line.trim();
                    if (line.isEmpty() || line.startsWith("#")) continue;
                    String[] parts = line.split("\\s+");
                    if ("newmtl".equals(parts[0]) && parts.length >= 2) {
                        currentMtl = parts[1];
                    } else if ("Kd".equals(parts[0]) && parts.length >= 4 && currentMtl != null) {
                        colors.put(currentMtl, new float[]{
                            Float.parseFloat(parts[1]),
                            Float.parseFloat(parts[2]),
                            Float.parseFloat(parts[3])
                        });
                    }
                }
            }
        } catch (Exception e) {
            LOGGER.warn("Failed to parse MTL: {}", mtlPath, e);
        }
        return colors;
    }

    private static String resolveMtlPath(ResourceLocation objLocation, String mtlFilename) {
        String objPath  = objLocation.getPath();
        int lastSlash   = Math.max(objPath.lastIndexOf('/'), objPath.lastIndexOf('\\'));
        String dir      = lastSlash >= 0 ? objPath.substring(0, lastSlash + 1) : "";
        return dir + mtlFilename;
    }

    // ── 面解析（回归 MTS 纯净风格：标准扇面三角化，不做任何绕序翻转）──

    /**
     * 解析一个面行（如 "f 1/1/1 2/2/2 3/3/3 4/4/4"），
     * 使用标准扇面三角化（v0, vi, vi+1）—— 完全对齐 MTS ModelParserOBJ 做法。
     * quad (a,b,c,d) → (a,b,c) + (a,c,d)
     * <p>
     * <b>不做任何 dot&lt;0 翻转或绕序修正</b>——理由：
     * <ul>
     *   <li>MTS 实测就是这样做的，零翻转逻辑，渲染正常</li>
     *   <li>自作聪明对调 v1/v2 会扯断相邻三角形共享边的 UV 连续性，
     *       导致 UV 梯度畸变 → 纹理高频平铺走样 → 密集斑马线条纹</li>
     *   <li>Blockbench Apply Transform 会把负缩放烘焙成正向绕序，
     *       模型导出时绕序已经是正确的</li>
     *   <li>渲染层用 entityCutoutNoCull 禁用背面剔除，即使有逆序面也可见</li>
     * </ul>
     * <p>
     * <b>UV V 轴翻转</b>：在 {@code case "vt"} 解析时已应用 {@code v = 1.0f - v}。
     */
    private static void parseFaceTo(String[] parts,
                                    List<float[]> positions,
                                    List<float[]> texCoords,
                                    List<float[]> normals,
                                    float[] matColor,
                                    List<ObjFace> target) {
        int vertexCount = parts.length - 1;
        if (vertexCount < 3) return;

        boolean hasNormals = !normals.isEmpty();

        // 纯粹解析，打包不可变顶点数据
        ObjVertex[] vertices = new ObjVertex[vertexCount];
        int validCount = 0;
        for (int i = 0; i < vertexCount; i++) {
            vertices[i] = resolveVertex(parts[i + 1], positions, texCoords, normals);
            if (vertices[i] != null) validCount++;
        }
        if (validCount < 3) return;

        // 绝对死板、绝不自作聪明的标准扇面三角化（完全对齐 MTS）
        for (int i = 1; i < vertexCount - 1; i++) {
            ObjVertex v0 = vertices[0];
            ObjVertex v1 = vertices[i];
            ObjVertex v2 = vertices[i + 1];
            if (v0 == null || v1 == null || v2 == null) continue;

            if (!hasNormals) {
                // 无顶点法线时用几何面法线（flat shading，唯一选择）
                float[] fn = computeFaceNormal(v0, v1, v2);
                v0 = new ObjVertex(v0.px, v0.py, v0.pz, v0.u, v0.v, fn[0], fn[1], fn[2]);
                v1 = new ObjVertex(v1.px, v1.py, v1.pz, v1.u, v1.v, fn[0], fn[1], fn[2]);
                v2 = new ObjVertex(v2.px, v2.py, v2.pz, v2.u, v2.v, fn[0], fn[1], fn[2]);
            }

            // 让位置和 UV 顺着 OBJ 导出的原本走向自然延伸
            target.add(new ObjFace(v0, v1, v2, matColor));
        }
    }

    // ── 顶点解析 ──

    private static ObjVertex resolveVertex(String ref,
                                           List<float[]> positions,
                                           List<float[]> texCoords,
                                           List<float[]> normals) {
        try {
            String[] indices = ref.split("/", -1);

            int posIdx = resolveIndex(Integer.parseInt(indices[0]), positions.size());
            if (posIdx < 0 || posIdx >= positions.size()) return null;
            float[] pos = positions.get(posIdx);

            float u = 0, v = 0;
            if (indices.length > 1 && !indices[1].isEmpty() && !texCoords.isEmpty()) {
                int uvIdx = resolveIndex(Integer.parseInt(indices[1]), texCoords.size());
                if (uvIdx >= 0 && uvIdx < texCoords.size()) {
                    u = texCoords.get(uvIdx)[0];
                    v = texCoords.get(uvIdx)[1];
                }
            }

            float nx = 0, ny = 1, nz = 0;
            if (indices.length > 2 && !indices[2].isEmpty() && !normals.isEmpty()) {
                int nIdx = resolveIndex(Integer.parseInt(indices[2]), normals.size());
                if (nIdx >= 0 && nIdx < normals.size()) {
                    nx = normals.get(nIdx)[0];
                    ny = normals.get(nIdx)[1];
                    nz = normals.get(nIdx)[2];
                }
            }

            return new ObjVertex(pos[0], pos[1], pos[2], u, v, nx, ny, nz);
        } catch (Exception e) {
            LOGGER.warn("Failed to resolve vertex: {}", ref);
            return null;
        }
    }

    private static int resolveIndex(int objIndex, int listSize) {
        if (objIndex > 0) return objIndex - 1;
        if (objIndex < 0) return listSize + objIndex;
        return -1;
    }

    private static float[] computeFaceNormal(ObjVertex v0, ObjVertex v1, ObjVertex v2) {
        float e1x = v1.px - v0.px, e1y = v1.py - v0.py, e1z = v1.pz - v0.pz;
        float e2x = v2.px - v0.px, e2y = v2.py - v0.py, e2z = v2.pz - v0.pz;
        float nx  = e1y * e2z - e1z * e2y;
        float ny  = e1z * e2x - e1x * e2z;
        float nz  = e1x * e2y - e1y * e2x;
        float len = (float) Math.sqrt(nx * nx + ny * ny + nz * nz);
        if (len > 1e-8f) { nx /= len; ny /= len; nz /= len; }
        return new float[]{nx, ny, nz};
    }
}
