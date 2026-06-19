# Vehicle Framework — CLAUDE.md

## 项目概览

MC 1.20.1 NeoForge (Legacy Forge 47.x) 的自定义 OBJ 载具模组。
不使用 GeckoLib、BakedModel、BlockModelRenderer，全部自研管线。

## 架构

```
OBJ 文件 → ObjLoader → ObjVertex/ObjFace → ObjModel → ObjModelCache
                                                          ↓
VehicleRenderer ← EntityRenderer<VehicleEntity>
       ↓
VertexConsumer (entityCutoutNoCull)
       ↓
MC Render Pipeline
```

## 构建 & 部署

```bash
cd /d/fjcvvv/MCmod_project/my-vehicle-framework
./gradlew build
cp build/libs/vehicleframework-0.1.0.jar "/c/Users/chang/AppData/Roaming/.minecraft/versions/1.20.1-NeoForge_47.1.106/mods/"
```

## 关键约定

### 渲染管线
- **RenderType**: `entityCutoutNoCull` — 不走 entitySolid（buffer 共享 + culling 问题）
- **PoseStack 逆序**: `mulPose → scale → translate` 调用顺序 = `translate → scale → rotate` 执行顺序
- **EntityRenderDispatcher 处理实体→相机平移**，Renderer 里不需要手动 camera translate
- **纹理预加载**: 构造函数里 `TextureManager.getTexture()` 强制注册

### OBJ 加载
- **Z-flip**: `z = -Float.parseFloat(parts[3])` — OBJ 右手系 → MC 左手系
- **UV**: 不翻转 V 轴 — Blockbench 导出 V=0 顶部，与 MC 一致
- **UV 钳制**: `Math.max(0, Math.min(1, u/v))` — 防边缘出血
- **扇形三角化**: 四边形 → 2 个三角形（默认绕序 v0,v1,v2）
- **boundsMax 初始化**: `-Float.MAX_VALUE` — 不能用 `Float.MIN_VALUE`（Java 里是最小正浮点）
- **MTL 路径**: 直接拼接 mtllib 声明的文件名，不强制替换为 OBJ 同名

### 数据类
- `ObjVertex.java` — 独立顶点类（px,py,pz / u,v / nx,ny,nz）
- `ObjFace.java` — 独立面类（v0,v1,v2 + 可选材质颜色）
- `ObjModel.java` — 模型容器（faces + boundsMin/Max）
- `ObjModelCache.java` — ConcurrentHashMap 缓存，懒加载

### 纹理
- 贴图路径: `textures/entity/vehicle/civic2008.png`
- `.mcmeta`: `blur: false, mipmap: false` — 最近邻采样，防色块渗透
- OBJ 模型: `models/obj/civic2008.obj`

## 文件清单

```
src/main/java/com/yourname/vehicleframework/
├── VehicleFramework.java              # @Mod 主入口
├── api/
│   └── IVehicleDriveable.java         # 驾驶接口
├── client/
│   ├── VehicleFrameworkClient.java    # 客户端初始化 + 渲染器注册
│   ├── camera/
│   │   └── VehicleCameraHandler.java  # FOV 缩放 + 骑乘摄像机同步
│   ├── input/
│   │   └── VehicleKeyInputHandler.java # WASD 驾驶输入
│   ├── model/
│   │   ├── ObjFace.java
│   │   ├── ObjVertex.java
│   │   ├── ObjModel.java
│   │   ├── ObjModelCache.java
│   │   └── ObjLoader.java
│   └── render/
│       ├── VehicleRenderer.java       # 主渲染器 ★
│       ├── ObjVehicleRenderer.java    # 备用渲染器（未注册）
│       └── BoatVehicleRenderer.java   # 旧船只渲染器（未注册）
├── common/
│   ├── entity/
│   │   ├── VehicleEntity.java         # 车辆实体 ★
│   │   ├── VehicleSeatEntity.java
│   │   └── VehicleCollisionEntity.java
│   ├── item/
│   │   ├── VehicleSpawnItem.java
│   │   ├── VehicleKeyItem.java
│   │   └── VehicleDismantleItem.java
│   ├── network/
│   │   ├── VehicleDrivingPacket.java
│   │   └── VehicleNetworkHandler.java
│   ├── physics/
│   │   ├── VehiclePhysicsEngine.java
│   │   ├── Wheel.java
│   │   └── CollisionCalculator.java
│   └── registry/
│       ├── ModEntityRegistry.java
│       └── ModItemRegistry.java
└── data/
    ├── VehicleType.java               # 车辆配置 record
    └── VehicleConfigLoader.java
```

## 已解决的问题

- ✅ 模型悬浮摄像机顶部 → EntityRenderDispatcher 处理位移 + 原生 vc.vertex()
- ✅ GeckoLib 完全移除
- ✅ 四边形三角化（扇面法）
- ✅ boundsMax 初始值修复（Float.MIN_VALUE → -Float.MAX_VALUE）
- ✅ UV 钳制（负值 → 0，溢出 → 1）
- ✅ 纹理 .mcmeta（关闭 blur/mipmap）
- ✅ hasUVs 按面判断 → 按顶点引用串判断

## 已知待解决

- MTL 材质名不匹配（OBJ 用 UUID，MTL 用 mat0~mat26），但当前不影响渲染（用贴图不用材质色）
- 镜像面可能被 backface culling 剔除 → 如需修复，加双面注入或逐个面翻转绕序
