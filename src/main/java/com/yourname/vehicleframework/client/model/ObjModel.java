package com.yourname.vehicleframework.client.model;

import java.util.List;

/**
 * 解析后的 OBJ 模型数据。
 * <p>
 * 存储三角化后的面片数据，供渲染器使用。
 * 每个 Face 包含 3 个顶点，每个顶点含位置、UV、法线。
 */
public class ObjModel {

    private final List<Face> faces;
    private final float[] boundsMin;
    private final float[] boundsMax;

    public ObjModel(List<Face> faces, float[] boundsMin, float[] boundsMax) {
        this.faces = faces;
        this.boundsMin = boundsMin;
        this.boundsMax = boundsMax;
    }

    public List<Face> getFaces() { return faces; }
    public float[] getBoundsMin() { return boundsMin; }
    public float[] getBoundsMax() { return boundsMax; }

    public float getSizeX() { return boundsMax[0] - boundsMin[0]; }
    public float getSizeY() { return boundsMax[1] - boundsMin[1]; }
    public float getSizeZ() { return boundsMax[2] - boundsMin[2]; }
    public float getMaxDimension() { return Math.max(getSizeX(), Math.max(getSizeY(), getSizeZ())); }

    /**
     * 三角面片。每个面包含 3 个顶点。
     */
    public static class Face {
        public final Vertex v0, v1, v2;

        public Face(Vertex v0, Vertex v1, Vertex v2) {
            this.v0 = v0;
            this.v1 = v1;
            this.v2 = v2;
        }
    }

    /**
     * 单个顶点数据。
     */
    public static class Vertex {
        public final float px, py, pz;   // 位置
        public final float u, v;          // UV 贴图坐标
        public final float nx, ny, nz;   // 法线

        public Vertex(float px, float py, float pz,
                      float u, float v,
                      float nx, float ny, float nz) {
            this.px = px; this.py = py; this.pz = pz;
            this.u = u;   this.v = v;
            this.nx = nx; this.ny = ny; this.nz = nz;
        }
    }
}
