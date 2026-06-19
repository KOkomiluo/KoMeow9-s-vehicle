package com.yourname.vehicleframework.client.model;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * OBJ 模型组——一个完整的 OBJ 文件的解析结果。
 * <p>
 * 仿 MTS 的 {@code List<RenderableVertices>} 做法：
 * 每个 "o name" 命名的子对象被解析为独立的 {@link ObjModel}，
 * 组内按 OBJ 文件中出现的顺序保持（LinkedHashMap）。
 * <p>
 * 同时维护整个模型的合并包围盒，用于渲染时的居中计算。
 */
public class ObjModelGroup {

    private final Map<String, ObjModel> objects;
    private final float[] boundsMin;
    private final float[] boundsMax;

    public ObjModelGroup(Map<String, ObjModel> objects,
                         float[] boundsMin, float[] boundsMax) {
        this.objects = Collections.unmodifiableMap(new LinkedHashMap<>(objects));
        this.boundsMin = boundsMin;
        this.boundsMax = boundsMax;
    }

    /** 获取所有命名子对象（保持 OBJ 中出现的顺序）。 */
    public Map<String, ObjModel> getObjects() {
        return objects;
    }

    /** 按名称获取子对象，不存在返回 null。 */
    public ObjModel getObject(String name) {
        return objects.get(name);
    }

    /** 整个模型的合并包围盒（所有子对象的最小/最大坐标）。 */
    public float[] getBoundsMin() { return boundsMin; }
    public float[] getBoundsMax() { return boundsMax; }

    /** 获取模型在 X 方向的尺寸。 */
    public float getSizeX() { return boundsMax[0] - boundsMin[0]; }
    /** 获取模型在 Y 方向的尺寸。 */
    public float getSizeY() { return boundsMax[1] - boundsMin[1]; }
    /** 获取模型在 Z 方向的尺寸。 */
    public float getSizeZ() { return boundsMax[2] - boundsMin[2]; }
    /** 获取模型的最大维度。 */
    public float getMaxDimension() {
        return Math.max(getSizeX(), Math.max(getSizeY(), getSizeZ()));
    }
}
