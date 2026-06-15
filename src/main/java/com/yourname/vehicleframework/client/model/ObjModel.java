package com.yourname.vehicleframework.client.model;

import java.util.List;

/**
 * 解析后的 OBJ 模型数据。
 * <p>
 * 存储三角化后的面片数据，供渲染器使用。
 */
public class ObjModel {

    private final List<ObjFace> faces;
    private final float[] boundsMin;
    private final float[] boundsMax;

    public ObjModel(List<ObjFace> faces, float[] boundsMin, float[] boundsMax) {
        this.faces = faces;
        this.boundsMin = boundsMin;
        this.boundsMax = boundsMax;
    }

    public List<ObjFace> getFaces()     { return faces; }
    public float[] getBoundsMin()       { return boundsMin; }
    public float[] getBoundsMax()       { return boundsMax; }

    public float getSizeX()             { return boundsMax[0] - boundsMin[0]; }
    public float getSizeY()             { return boundsMax[1] - boundsMin[1]; }
    public float getSizeZ()             { return boundsMax[2] - boundsMin[2]; }
    public float getMaxDimension()      { return Math.max(getSizeX(), Math.max(getSizeY(), getSizeZ())); }
}
