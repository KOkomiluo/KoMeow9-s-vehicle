package com.yourname.vehicleframework.client.model;

/**
 * OBJ 模型三角面片。
 * <p>
 * 包含 3 个顶点引用和可选的 MTL 材质颜色。
 */
public class ObjFace {

    public final ObjVertex v0, v1, v2;
    /** 材质颜色 RGB（0~1），无材质时为 null。 */
    public final float[] color;

    public ObjFace(ObjVertex v0, ObjVertex v1, ObjVertex v2) {
        this(v0, v1, v2, null);
    }

    public ObjFace(ObjVertex v0, ObjVertex v1, ObjVertex v2, float[] color) {
        this.v0 = v0;
        this.v1 = v1;
        this.v2 = v2;
        this.color = color;
    }

    /** 是否有有效的材质颜色。 */
    public boolean hasColor() {
        return color != null && color.length >= 3;
    }
}
