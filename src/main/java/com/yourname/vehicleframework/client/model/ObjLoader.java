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
import java.util.List;
import java.util.Optional;

/**
 * OBJ 模型解析器。
 * <p>
 * 支持标准 Wavefront OBJ 格式：
 * - v  (顶点位置)
 * - vn (顶点法线)
 * - vt (纹理坐标)
 * - f  (面，支持三角形和四边形，自动三角化)
 * <p>
 * 坐标转换：
 * - OBJ 的 Y-up 转换为 Minecraft 的 Y-up
 * - OBJ 的右手坐标系转换为 Minecraft 的左手坐标系（Z 取反）
 * - 自动缩放到 Minecraft 方块单位（1 格 = 16 OBJ 单位）
 */
public final class ObjLoader {

    private ObjLoader() {}

    private static final Logger LOGGER = VehicleFramework.LOGGER;

    /**
     * 从 Minecraft 资源系统加载并解析 OBJ 文件。
     *
     * @param resourceLocation OBJ 文件的资源位置
     *                         例如: vehicleframework:models/obj/sports_car.obj
     * @return 解析后的模型，失败返回 null
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
                return parse(stream);
            }
        } catch (Exception e) {
            LOGGER.error("Failed to load OBJ model: {}", resourceLocation, e);
            return null;
        }
    }

    /**
     * 从输入流解析 OBJ 文件。
     */
    public static ObjModel parse(InputStream inputStream) throws Exception {
        List<float[]> positions = new ArrayList<>();
        List<float[]> normals = new ArrayList<>();
        List<float[]> texCoords = new ArrayList<>();
        List<ObjModel.Face> faces = new ArrayList<>();

        float minX = Float.MAX_VALUE, minY = Float.MAX_VALUE, minZ = Float.MAX_VALUE;
        float maxX = Float.MIN_VALUE, maxY = Float.MIN_VALUE, maxZ = Float.MIN_VALUE;

        BufferedReader reader = new BufferedReader(new InputStreamReader(inputStream, StandardCharsets.UTF_8));
        String line;

        while ((line = reader.readLine()) != null) {
            line = line.trim();
            if (line.isEmpty() || line.startsWith("#")) continue;

            String[] parts = line.split("\\s+");
            String keyword = parts[0];

            switch (keyword) {
                case "v": // 顶点位置: v x y z
                    if (parts.length >= 4) {
                        float x = Float.parseFloat(parts[1]);
                        float y = Float.parseFloat(parts[2]);
                        float z = -Float.parseFloat(parts[3]); // 翻转 Z 轴（OBJ右手→MC左手）
                        positions.add(new float[]{x, y, z});
                        minX = Math.min(minX, x); maxX = Math.max(maxX, x);
                        minY = Math.min(minY, y); maxY = Math.max(maxY, y);
                        minZ = Math.min(minZ, z); maxZ = Math.max(maxZ, z);
                    }
                    break;

                case "vn": // 法线: vn nx ny nz
                    if (parts.length >= 4) {
                        float nx = Float.parseFloat(parts[1]);
                        float ny = Float.parseFloat(parts[2]);
                        float nz = -Float.parseFloat(parts[3]); // 翻转 Z
                        normals.add(new float[]{nx, ny, nz});
                    }
                    break;

                case "vt": // 纹理坐标: vt u v
                    if (parts.length >= 3) {
                        float u = Float.parseFloat(parts[1]);
                        float v = 1.0f - Float.parseFloat(parts[2]); // 翻转 V（OBJ 的 V 轴朝上）
                        texCoords.add(new float[]{u, v});
                    }
                    break;

                case "f": // 面: f v1/vt1/vn1 v2/vt2/vn2 v3/vt3/vn3 ...
                    parseFace(parts, positions, texCoords, normals, faces);
                    break;

                // 忽略其他行（o, g, s, usemtl, mtllib 等）
            }
        }

        LOGGER.info("OBJ parsed: {} positions, {} normals, {} texcoords, {} faces",
                positions.size(), normals.size(), texCoords.size(), faces.size());

        float[] boundsMin = {minX, minY, minZ};
        float[] boundsMax = {maxX, maxY, maxZ};
        return new ObjModel(faces, boundsMin, boundsMax);
    }

    /**
     * 解析面数据并三角化。
     * 支持格式: f v, f v/vt, f v/vt/vn, f v//vn
     * 支持三角形、四边形（拆为两个三角形）和多边形（扇形三角化）。
     * 当模型缺少法线时，自动计算面法线。
     */
    private static void parseFace(String[] parts,
                                  List<float[]> positions,
                                  List<float[]> texCoords,
                                  List<float[]> normals,
                                  List<ObjModel.Face> faces) {
        // parts[0] = "f", parts[1..n] = 顶点引用
        int vertexCount = parts.length - 1;
        if (vertexCount < 3) return;

        boolean hasNormals = !normals.isEmpty();

        ObjModel.Vertex[] vertices = new ObjModel.Vertex[vertexCount];
        for (int i = 0; i < vertexCount; i++) {
            vertices[i] = resolveVertex(parts[i + 1], positions, texCoords, normals);
            if (vertices[i] == null) return; // 解析失败，跳过该面
        }

        // 扇形三角化：将 N 边形拆为 (N-2) 个三角形
        for (int i = 1; i < vertexCount - 1; i++) {
            ObjModel.Vertex v0 = vertices[0];
            ObjModel.Vertex v1 = vertices[i];
            ObjModel.Vertex v2 = vertices[i + 1];

            // 如果模型没有法线数据，自动计算面法线
            if (!hasNormals) {
                float[] fn = computeFaceNormal(v0, v1, v2);
                float nx = fn[0], ny = fn[1], nz = fn[2];
                v0 = new ObjModel.Vertex(v0.px, v0.py, v0.pz, v0.u, v0.v, nx, ny, nz);
                v1 = new ObjModel.Vertex(v1.px, v1.py, v1.pz, v1.u, v1.v, nx, ny, nz);
                v2 = new ObjModel.Vertex(v2.px, v2.py, v2.pz, v2.u, v2.v, nx, ny, nz);
            }

            faces.add(new ObjModel.Face(v0, v1, v2));
        }
    }

    /**
     * 通过叉积计算三角面法线。
     * normal = (v1-v0) × (v2-v0)，然后归一化。
     */
    private static float[] computeFaceNormal(ObjModel.Vertex v0, ObjModel.Vertex v1, ObjModel.Vertex v2) {
        float e1x = v1.px - v0.px, e1y = v1.py - v0.py, e1z = v1.pz - v0.pz;
        float e2x = v2.px - v0.px, e2y = v2.py - v0.py, e2z = v2.pz - v0.pz;

        float nx = e1y * e2z - e1z * e2y;
        float ny = e1z * e2x - e1x * e2z;
        float nz = e1x * e2y - e1y * e2x;

        float len = (float) Math.sqrt(nx * nx + ny * ny + nz * nz);
        if (len > 1e-8f) {
            nx /= len; ny /= len; nz /= len;
        }
        return new float[]{nx, ny, nz};
    }

    /**
     * 解析单个顶点引用 (如 "1/2/3", "1//3", "1/2", "1")。
     * 索引从 1 开始，支持负索引（从末尾倒数）。
     */
    private static ObjModel.Vertex resolveVertex(String ref,
                                                 List<float[]> positions,
                                                 List<float[]> texCoords,
                                                 List<float[]> normals) {
        String[] indices = ref.split("/", -1);

        // 位置索引（必须）
        int posIdx = resolveIndex(Integer.parseInt(indices[0]), positions.size());
        if (posIdx < 0 || posIdx >= positions.size()) return null;
        float[] pos = positions.get(posIdx);

        // UV 索引（可选）
        float u = 0, v = 0;
        if (indices.length > 1 && !indices[1].isEmpty()) {
            int uvIdx = resolveIndex(Integer.parseInt(indices[1]), texCoords.size());
            if (uvIdx >= 0 && uvIdx < texCoords.size()) {
                float[] uv = texCoords.get(uvIdx);
                u = uv[0];
                v = uv[1];
            }
        }

        // 法线索引（可选）
        float nx = 0, ny = 1, nz = 0; // 默认朝上法线
        if (indices.length > 2 && !indices[2].isEmpty()) {
            int nIdx = resolveIndex(Integer.parseInt(indices[2]), normals.size());
            if (nIdx >= 0 && nIdx < normals.size()) {
                float[] n = normals.get(nIdx);
                nx = n[0]; ny = n[1]; nz = n[2];
            }
        }

        return new ObjModel.Vertex(pos[0], pos[1], pos[2], u, v, nx, ny, nz);
    }

    /**
     * 将 OBJ 索引（1-based，支持负索引）转换为 0-based 索引。
     */
    private static int resolveIndex(int objIndex, int listSize) {
        if (objIndex > 0) return objIndex - 1;
        if (objIndex < 0) return listSize + objIndex;
        return -1; // 无效索引
    }
}
