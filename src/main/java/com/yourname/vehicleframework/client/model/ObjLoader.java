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

public final class ObjLoader {

    private ObjLoader() {}

    private static final Logger LOGGER = VehicleFramework.LOGGER;

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

    public static ObjModel parse(InputStream inputStream, ResourceLocation objLocation) throws Exception {
        List<float[]> positions = new ArrayList<>();
        List<float[]> normals   = new ArrayList<>();
        List<float[]> texCoords = new ArrayList<>();
        List<ObjFace> faces     = new ArrayList<>();

        float minX =  Float.MAX_VALUE, minY =  Float.MAX_VALUE, minZ =  Float.MAX_VALUE;
        float maxX = -Float.MAX_VALUE, maxY = -Float.MAX_VALUE, maxZ = -Float.MAX_VALUE;

        Map<String, float[]> materialColors = new HashMap<>();
        String currentMaterial = null;

        BufferedReader reader = new BufferedReader(new InputStreamReader(inputStream, StandardCharsets.UTF_8));
        String line;

        while ((line = reader.readLine()) != null) {
            line = line.trim();
            if (line.isEmpty() || line.startsWith("#")) continue;

            String[] parts   = line.split("\\s+");
            String   keyword = parts[0];

            switch (keyword) {
                case "mtllib":
                    if (parts.length >= 2) {
                        String mtlPath = resolveMtlPath(objLocation, parts[1]);
                        materialColors = loadMaterialColors(mtlPath);
                        LOGGER.info("MTL loaded: {} materials from {}", materialColors.size(), mtlPath);
                    }
                    break;

                case "usemtl":
                    if (parts.length >= 2) currentMaterial = parts[1];
                    break;

                case "v":
                    if (parts.length >= 4) {
                        float x =  Float.parseFloat(parts[1]);
                        float y =  Float.parseFloat(parts[2]);
                        float z = -Float.parseFloat(parts[3]); // Z flip: 右手系 → 左手系
                        positions.add(new float[]{x, y, z});
                        minX = Math.min(minX, x); maxX = Math.max(maxX, x);
                        minY = Math.min(minY, y); maxY = Math.max(maxY, y);
                        minZ = Math.min(minZ, z); maxZ = Math.max(maxZ, z);
                    }
                    break;

                case "vn":
                    if (parts.length >= 4) {
                        float nx =  Float.parseFloat(parts[1]);
                        float ny =  Float.parseFloat(parts[2]);
                        float nz = -Float.parseFloat(parts[3]); // Z flip
                        normals.add(new float[]{nx, ny, nz});
                    }
                    break;

                case "vt":
                    if (parts.length >= 3) {
                        float u =        Float.parseFloat(parts[1]);
                        float v = 1.0f - Float.parseFloat(parts[2]); // OBJ V=0底部 → MC V=0顶部
                        texCoords.add(new float[]{u, v});
                    }
                    break;

                case "f":
                    float[] matColor = materialColors.get(currentMaterial);
                    parseFace(parts, positions, texCoords, normals, matColor, faces);
                    break;
            }
        }

        LOGGER.info("OBJ parsed: {} vertices, {} faces, {} uvs, {} normals",
                positions.size(), faces.size(), texCoords.size(), normals.size());

        float[] boundsMin = {minX, minY, minZ};
        float[] boundsMax = {maxX, maxY, maxZ};
        return new ObjModel(faces, boundsMin, boundsMax);
    }

    // ── MTL ──

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
        return dir + mtlFilename; // 使用 mtllib 声明的文件名，不强制替换
    }

    // ── 面解析 ──

    private static void parseFace(String[] parts,
                                  List<float[]> positions,
                                  List<float[]> texCoords,
                                  List<float[]> normals,
                                  float[] matColor,
                                  List<ObjFace> faces) {
        int vertexCount = parts.length - 1;
        if (vertexCount < 3) return;

        boolean hasNormals = !normals.isEmpty();

        ObjVertex[] vertices = new ObjVertex[vertexCount];
        for (int i = 0; i < vertexCount; i++) {
            vertices[i] = resolveVertex(parts[i + 1], positions, texCoords, normals);
            if (vertices[i] == null) return;
        }

        // 扇形三角化
        // Z 轴取反后绕序从 CCW 变为 CW，交换 v1/v2 恢复正确绕序
        for (int i = 1; i < vertexCount - 1; i++) {
            ObjVertex v0 = vertices[0];
            ObjVertex v1 = vertices[i + 1]; // ← 交换（补偿 Z flip 的绕序翻转）
            ObjVertex v2 = vertices[i];     // ← 交换

            if (!hasNormals) {
                float[] fn = computeFaceNormal(v0, v1, v2);
                v0 = new ObjVertex(v0.px, v0.py, v0.pz, v0.u, v0.v, fn[0], fn[1], fn[2]);
                v1 = new ObjVertex(v1.px, v1.py, v1.pz, v1.u, v1.v, fn[0], fn[1], fn[2]);
                v2 = new ObjVertex(v2.px, v2.py, v2.pz, v2.u, v2.v, fn[0], fn[1], fn[2]);
            }

            faces.add(new ObjFace(v0, v1, v2, matColor));
        }
    }

    private static ObjVertex resolveVertex(String ref,
                                           List<float[]> positions,
                                           List<float[]> texCoords,
                                           List<float[]> normals) {
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
