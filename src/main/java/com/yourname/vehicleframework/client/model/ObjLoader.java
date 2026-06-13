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
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * OBJ 模型解析器 + MTL 材质解析。
 * <p>
 * 支持标准 Wavefront OBJ 格式：
 * - v/vn/vt 顶点数据
 * - f 面（三角形/四边形/多边形 → 自动三角化）
 * - usemtl 材质引用 + MTL 文件解析（Kd 漫反射颜色）
 * <p>
 * 坐标转换：OBJ Y-up 右手系 → MC Y-up 左手系（Z 取反）。
 */
public final class ObjLoader {

    private ObjLoader() {}

    private static final Logger LOGGER = VehicleFramework.LOGGER;

    /**
     * 从 Minecraft 资源系统加载并解析 OBJ 文件。
     */
    public static ObjModel load(ResourceLocation resourceLocation) {
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

    /**
     * 从输入流解析 OBJ 文件。
     */
    public static ObjModel parse(InputStream inputStream, ResourceLocation objLocation) throws Exception {
        List<float[]> positions = new ArrayList<>();
        List<float[]> normals = new ArrayList<>();
        List<float[]> texCoords = new ArrayList<>();
        List<ObjModel.Face> faces = new ArrayList<>();

        float minX = Float.MAX_VALUE, minY = Float.MAX_VALUE, minZ = Float.MAX_VALUE;
        float maxX = Float.MIN_VALUE, maxY = Float.MIN_VALUE, maxZ = Float.MIN_VALUE;

        // MTL 材质 → RGB 颜色映射
        Map<String, float[]> materialColors = new HashMap<>();
        String currentMaterial = null;

        BufferedReader reader = new BufferedReader(new InputStreamReader(inputStream, StandardCharsets.UTF_8));
        String line;

        while ((line = reader.readLine()) != null) {
            line = line.trim();
            if (line.isEmpty() || line.startsWith("#")) continue;

            String[] parts = line.split("\\s+");
            String keyword = parts[0];

            switch (keyword) {
                case "mtllib":
                    // 解析 MTL 文件获取材质颜色
                    if (parts.length >= 2) {
                        String mtlPath = resolveMtlPath(objLocation, parts[1]);
                        materialColors = loadMaterialColors(mtlPath);
                        LOGGER.info("MTL loaded: {} materials from {}", materialColors.size(), mtlPath);
                    }
                    break;

                case "usemtl":
                    if (parts.length >= 2) {
                        currentMaterial = parts[1];
                    }
                    break;

                case "v":
                    if (parts.length >= 4) {
                        float x = Float.parseFloat(parts[1]);
                        float y = Float.parseFloat(parts[2]);
                        float z = -Float.parseFloat(parts[3]); // Z flip
                        positions.add(new float[]{x, y, z});
                        minX = Math.min(minX, x); maxX = Math.max(maxX, x);
                        minY = Math.min(minY, y); maxY = Math.max(maxY, y);
                        minZ = Math.min(minZ, z); maxZ = Math.max(maxZ, z);
                    }
                    break;

                case "vn":
                    if (parts.length >= 4) {
                        float nx = Float.parseFloat(parts[1]);
                        float ny = Float.parseFloat(parts[2]);
                        float nz = -Float.parseFloat(parts[3]);
                        normals.add(new float[]{nx, ny, nz});
                    }
                    break;

                case "vt":
                    if (parts.length >= 3) {
                        float u = Float.parseFloat(parts[1]);
                        float v = Float.parseFloat(parts[2]); // Blockbench exports image-space UV (V=0 top), same as MC
                        texCoords.add(new float[]{u, v});
                    }
                    break;

                case "f":
                    float[] matColor = materialColors.get(currentMaterial);
                    parseFace(parts, positions, texCoords, normals, matColor, faces);
                    break;
            }
        }

        boolean hasUVs = !texCoords.isEmpty();
        LOGGER.info("OBJ parsed: {} vertices, {} faces, {} uvs, {} normals, {} materials (hasUVs={})",
                positions.size(), faces.size(), texCoords.size(), normals.size(),
                materialColors.size(), hasUVs);

        float[] boundsMin = {minX, minY, minZ};
        float[] boundsMax = {maxX, maxY, maxZ};
        return new ObjModel(faces, boundsMin, boundsMax);
    }

    // ── MTL 材质解析 ──

    /** 从资源系统加载 MTL 文件，解析 Kd（漫反射颜色）。 */
    private static Map<String, float[]> loadMaterialColors(String mtlPath) {
        Map<String, float[]> colors = new HashMap<>();
        try {
            ResourceManager rm = net.minecraft.client.Minecraft.getInstance().getResourceManager();
            // 使用 vehicleframework 命名空间（mod 资源）
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
                        float r = Float.parseFloat(parts[1]);
                        float g = Float.parseFloat(parts[2]);
                        float b = Float.parseFloat(parts[3]);
                        colors.put(currentMtl, new float[]{r, g, b});
                    }
                }
            }
        } catch (Exception e) {
            LOGGER.warn("Failed to parse MTL: {}", mtlPath, e);
        }
        return colors;
    }

    /** 根据 OBJ 位置和 mtllib 引用构建 MTL 文件路径。 */
    private static String resolveMtlPath(ResourceLocation objLocation, String mtlFilename) {
        String objPath = objLocation.getPath();
        int lastSlash = Math.max(objPath.lastIndexOf('/'), objPath.lastIndexOf('\\'));
        String dir = lastSlash >= 0 ? objPath.substring(0, lastSlash + 1) : "";
        // 优先使用 OBJ 自身的文件名（不含扩展名）+ ".mtl"，
        // 因为资源文件名可能已标准化（如空格→下划线）
        String objStem = objPath.substring(lastSlash + 1);
        int dot = objStem.lastIndexOf('.');
        if (dot > 0) objStem = objStem.substring(0, dot);
        return dir + objStem + ".mtl";
    }

    // ── 面解析 ──

    private static void parseFace(String[] parts,
                                  List<float[]> positions,
                                  List<float[]> texCoords,
                                  List<float[]> normals,
                                  float[] matColor,
                                  List<ObjModel.Face> faces) {
        int vertexCount = parts.length - 1;
        if (vertexCount < 3) return;

        boolean hasNormals = !normals.isEmpty();
        boolean hasUVs = !texCoords.isEmpty();

        ObjModel.Vertex[] vertices = new ObjModel.Vertex[vertexCount];
        for (int i = 0; i < vertexCount; i++) {
            vertices[i] = resolveVertex(parts[i + 1], positions, texCoords, normals, hasUVs);
            if (vertices[i] == null) return;
        }

        // 扇形三角化
        for (int i = 1; i < vertexCount - 1; i++) {
            ObjModel.Vertex v0 = vertices[0];
            ObjModel.Vertex v1 = vertices[i];
            ObjModel.Vertex v2 = vertices[i + 1];

            if (!hasNormals) {
                float[] fn = computeFaceNormal(v0, v1, v2);
                v0 = new ObjModel.Vertex(v0.px, v0.py, v0.pz, v0.u, v0.v, fn[0], fn[1], fn[2]);
                v1 = new ObjModel.Vertex(v1.px, v1.py, v1.pz, v1.u, v1.v, fn[0], fn[1], fn[2]);
                v2 = new ObjModel.Vertex(v2.px, v2.py, v2.pz, v2.u, v2.v, fn[0], fn[1], fn[2]);
            }

            // 交换 v1/v2 反转绕序：Z 轴取反后右手系→左手系，需补偿绕序
            faces.add(new ObjModel.Face(v0, v2, v1, matColor));
        }
    }

    private static ObjModel.Vertex resolveVertex(String ref,
                                                  List<float[]> positions,
                                                  List<float[]> texCoords,
                                                  List<float[]> normals,
                                                  boolean hasUVs) {
        String[] indices = ref.split("/", -1);

        int posIdx = resolveIndex(Integer.parseInt(indices[0]), positions.size());
        if (posIdx < 0 || posIdx >= positions.size()) return null;
        float[] pos = positions.get(posIdx);

        float u = 0, v = 0;
        if (hasUVs && indices.length > 1 && !indices[1].isEmpty()) {
            int uvIdx = resolveIndex(Integer.parseInt(indices[1]), texCoords.size());
            if (uvIdx >= 0 && uvIdx < texCoords.size()) {
                float[] uv = texCoords.get(uvIdx);
                u = uv[0]; v = uv[1];
            }
        }

        float nx = 0, ny = 1, nz = 0;
        if (indices.length > 2 && !indices[2].isEmpty()) {
            int nIdx = resolveIndex(Integer.parseInt(indices[2]), normals.size());
            if (nIdx >= 0 && nIdx < normals.size()) {
                float[] n = normals.get(nIdx);
                nx = n[0]; ny = n[1]; nz = n[2];
            }
        }

        return new ObjModel.Vertex(pos[0], pos[1], pos[2], u, v, nx, ny, nz);
    }

    private static int resolveIndex(int objIndex, int listSize) {
        if (objIndex > 0) return objIndex - 1;
        if (objIndex < 0) return listSize + objIndex;
        return -1;
    }

    private static float[] computeFaceNormal(ObjModel.Vertex v0, ObjModel.Vertex v1, ObjModel.Vertex v2) {
        float e1x = v1.px - v0.px, e1y = v1.py - v0.py, e1z = v1.pz - v0.pz;
        float e2x = v2.px - v0.px, e2y = v2.py - v0.py, e2z = v2.pz - v0.pz;
        float nx = e1y * e2z - e1z * e2y;
        float ny = e1z * e2x - e1x * e2z;
        float nz = e1x * e2y - e1y * e2x;
        float len = (float) Math.sqrt(nx * nx + ny * ny + nz * nz);
        if (len > 1e-8f) { nx /= len; ny /= len; nz /= len; }
        return new float[]{nx, ny, nz};
    }
}
