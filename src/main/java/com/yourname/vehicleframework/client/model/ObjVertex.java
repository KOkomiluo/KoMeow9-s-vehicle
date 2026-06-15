package com.yourname.vehicleframework.client.model;

/**
 * OBJ 模型单个顶点数据。
 * <p>
 * 包含位置、UV 贴图坐标、法线，均为不可变数据。
 */
public class ObjVertex {

    public final float px, py, pz;   // 位置 (MC 坐标系，Z 已取反)
    public final float u, v;          // UV 贴图坐标 (0~1 归一化)
    public final float nx, ny, nz;   // 法线向量

    public ObjVertex(float px, float py, float pz,
                     float u, float v,
                     float nx, float ny, float nz) {
        this.px = px; this.py = py; this.pz = pz;
        this.u  = u;  this.v  = v;
        this.nx = nx; this.ny = ny; this.nz = nz;
    }
}
