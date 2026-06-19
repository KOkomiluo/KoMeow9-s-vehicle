package com.yourname.vehicleframework.client.model;

import java.util.Collections;
import java.util.List;

/**
 * 解析后的 OBJ 模型子对象数据。
 * <p>
 * 仿 MTS 的做法，每个 OBJ 文件中的命名对象（"o name"）被解析为独立的 ObjModel。
 * 这允许后续独立控制每个子对象的变换（如车轮旋转、车门开闭等动画）。
 */
public class ObjModel {

    private final String name;
    private final List<ObjFace> faces;
    private final float[] boundsMin;
    private final float[] boundsMax;

    public ObjModel(String name, List<ObjFace> faces, float[] boundsMin, float[] boundsMax) {
        this.name = name;
        this.faces = Collections.unmodifiableList(faces);
        this.boundsMin = boundsMin;
        this.boundsMax = boundsMax;
    }

    public String getName()             { return name; }
    public List<ObjFace> getFaces()     { return faces; }
    public float[] getBoundsMin()       { return boundsMin; }
    public float[] getBoundsMax()       { return boundsMax; }

    public float getSizeX()             { return boundsMax[0] - boundsMin[0]; }
    public float getSizeY()             { return boundsMax[1] - boundsMin[1]; }
    public float getSizeZ()             { return boundsMax[2] - boundsMin[2]; }
    public float getMaxDimension()      { return Math.max(getSizeX(), Math.max(getSizeY(), getSizeZ())); }
}
