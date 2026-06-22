# Vehicle Framework

面向 **Minecraft 1.20.1 / Forge 47.x（NeoForge ModDev LegacyForge）** 的自定义 OBJ 载具框架。

项目目前包含服务器权威的 Simcade 车辆物理、自研 Wavefront OBJ 加载与实体渲染、车辆配置、驾驶输入同步，以及客户端平滑姿态和摄像机处理。

> 当前版本：`0.1.0`
>
> 当前演示车辆：Honda Civic 2008 半成品模型

## 当前功能

- JSON 驱动的车辆性能参数
- WASD 驾驶与空格手刹
- 前后轴动态自行车模型
- 横向惯性、轮胎侧偏与可控侧滑
- 渐进式油门、制动和转向输入
- 自动变速、发动机转速和发动机制动
- 滚动阻力与空气阻力
- 四轮射线接地和弹簧阻尼悬挂
- 加速抬头、刹车点头和转弯侧倾
- 根据四轮接地点计算坡面俯仰与侧倾
- 低速一格台阶辅助
- 服务器权威物理与客户端视觉插值
- yaw 最短角插值，避免大幅转向时跨 `±180°` 抽搐
- 自研 OBJ/MTL 解析、缓存和实体渲染
- 支持 OBJ 四边形三角化、UV、法线和命名对象
- 动态 FOV 与乘员位置同步

## 操作

| 按键 | 行为 |
| --- | --- |
| `W` | 油门；倒车时先制动，停车后切回前进挡 |
| `S` | 制动；停车后进入倒挡并倒车 |
| `A` / `D` | 左右转向 |
| `Space` | 手刹，降低后轴抓地力以产生侧滑 |
| 右键车辆 | 进入驾驶位 |
| 拆卸工具右键车辆 | 回收车辆生成物 |

## 开发环境

| 组件 | 版本 |
| --- | --- |
| Minecraft | 1.20.1 |
| Forge | 47.1.3 |
| ModDev 插件 | LegacyForge |
| Java Toolchain | 17 |
| Gradle Wrapper | 8.12 |

推荐使用 Eclipse Temurin JDK 17。使用较新 JDK 启动 Gradle 时，项目仍会通过 Java Toolchain 编译为 Java 17。

## 构建与运行

Windows PowerShell：

```powershell
.\gradlew.bat build
.\gradlew.bat runClient
```

Linux/macOS：

```bash
./gradlew build
./gradlew runClient
```

构建产物：

```text
build/libs/vehicleframework-0.1.0.jar
```

部署到当前测试实例：

```powershell
Copy-Item `
  -LiteralPath "build\libs\vehicleframework-0.1.0.jar" `
  -Destination "$env:APPDATA\.minecraft\versions\1.20.1-NeoForge_47.1.106\mods\vehicleframework-0.1.0.jar" `
  -Force
```

替换 JAR 后需要完全退出并重新启动 Minecraft。

## 架构

```text
客户端按键
    ↓ VehicleDrivingPacket
服务端 VehicleEntity
    ↓
VehiclePhysicsEngine
    ├─ 平滑输入
    ├─ 发动机、传动和纵向阻力
    ├─ 动态自行车模型与轮胎侧偏
    ├─ 四轮悬挂与地面检测
    ├─ 车身 pitch / roll
    └─ Minecraft 碰撞移动
    ↓ 实体与 SynchedEntityData
客户端视觉插值
    ├─ 最短角 visual yaw
    ├─ body pitch / roll
    ├─ 乘员位置
    └─ VehicleRenderer
```

主要代码：

```text
src/main/java/com/yourname/vehicleframework/
├── common/
│   ├── entity/VehicleEntity.java
│   ├── physics/VehiclePhysicsEngine.java
│   ├── physics/Wheel.java
│   └── network/VehicleDrivingPacket.java
├── client/
│   ├── model/ObjLoader.java
│   ├── model/ObjModelCache.java
│   ├── render/VehicleRenderer.java
│   ├── input/VehicleKeyInputHandler.java
│   └── camera/VehicleCameraHandler.java
└── data/
    ├── VehicleType.java
    └── VehicleConfigLoader.java
```

## Simcade 物理

车辆水平速度是一个真实二维向量，不会被每 tick 强制覆盖为“车头方向 × 速度”。

| 系统 | 当前实现 |
| --- | --- |
| 转向 | 低速运动学转向与中高速动态自行车模型平滑混合 |
| 横向运动 | 根据前后轴侧滑角计算横向加速度和 yaw 角速度 |
| 手刹 | 后轴抓地力渐进降低，松开后渐进恢复 |
| 纵向运动 | 发动机驱动力、制动力、发动机制动、滚阻和风阻 |
| 输入 | 油门、制动、方向输入按响应速度渐进变化 |
| 悬挂 | 四轮射线检测，弹簧与阻尼产生竖直反馈 |
| 车身姿态 | 地形高度、纵向加速度和横向加速度共同决定 pitch/roll |
| 台阶 | 多轮接地且低速时临时允许约一格自动跨步 |
| 网络 | 服务端计算物理，客户端只负责插值与渲染 |

### 视觉旋转

服务端网络 yaw 不会直接瞬间写入画面。客户端维护独立的视觉 yaw：

- 使用 `Mth.wrapDegrees` 选择最短旋转路径
- 限制单 tick 视觉角速度
- 使用阻尼追踪服务端目标
- 模型、乘员位置和摄像机使用同一个视觉 yaw
- 瞬移或异常大角度校正时重置插值状态

这可以避免车辆连续转圈并跨越 `180°/-180°` 时发生反向旋转或数帧大幅抽搐。

## OBJ 渲染

```text
OBJ
 └─ ObjLoader
     ├─ 解析 v / vt / vn / f
     ├─ 按 o / g 拆分命名对象
     ├─ 四边形扇形三角化
     ├─ OBJ 索引与负索引处理
     ├─ 坐标轴转换
     └─ MTL 漫反射颜色读取
          ↓
     ObjModelGroup
          ↓
     ObjModelCache
          ↓
     VehicleRenderer
          ↓
     entityCutoutNoCull
```

Minecraft 的实体 RenderType 以四边形消费顶点。加载器内部保存三角面，渲染时会重复三角形最后一个顶点，构造退化第四顶点，避免相邻 OBJ 三角形被错误连接成巨大残面。

当前贴图使用 `entityCutoutNoCull`：

- 透明 UV 图集背景会被裁切
- 不透明车身正常写入深度
- 双面渲染，降低 OBJ 绕序不一致造成的缺面

### 坐标转换

`ObjLoader` 当前配置：

```java
FLIP_X = -1.0f;
FLIP_Y =  1.0f;
FLIP_Z = -1.0f;
```

UV 的 V 坐标在加载时使用：

```java
v = 1.0f - sourceV;
```

如果替换来源不同的模型，需要重新检查朝向、镜像和 UV 原点约定。

## 车辆 JSON 配置

配置目录：

```text
src/main/resources/data/vehicleframework/vehicles/
```

示例：

```json
{
  "id": "sports_car",
  "displayName": "Sports Car",
  "maxSpeed": 1.2,
  "acceleration": 0.02,
  "brakingPower": 0.05,
  "fuelCapacity": 120.0,
  "weight": 1200.0,

  "objModelPath": "models/obj/civic2008.obj",
  "objScale": 0.5,
  "texturePath": "civic2008",

  "driveType": "rwd",
  "gearRatios": [-3.5, 0.0, 3.5, 2.2, 1.5, 1.1, 0.85, 0.7],
  "finalDriveRatio": 3.5,
  "enginePeakTorque": 300.0,
  "enginePeakRPM": 4000.0,

  "tirePeakFriction": 1.0,
  "tireSlidingFriction": 0.7,
  "tireLateralStiffness": 10.0,

  "wheelBase": 3.2,
  "trackWidth": 2.0,
  "yawInertia": 2.6,
  "aerodynamicDrag": 0.006,
  "rollingResistance": 0.0015,
  "maxSteeringAngle": 35.0,
  "inputResponse": 0.18,
  "bodyPitchStrength": 85.0,
  "bodyRollStrength": 110.0
}
```

### 主要调参

| 字段 | 作用 |
| --- | --- |
| `maxSpeed` | 最大前进速度，单位约为 blocks/tick |
| `acceleration` | 基础驱动加速度 |
| `brakingPower` | 行车制动力 |
| `weight` | 车辆质量和轮胎力计算基准 |
| `driveType` | `rwd`、`fwd` 或 `awd` |
| `wheelBase` | 前后轴距离 |
| `trackWidth` | 左右轮距 |
| `yawInertia` | 车身偏航惯量；越大越不容易快速转头 |
| `tireLateralStiffness` | 轮胎侧偏响应强度 |
| `aerodynamicDrag` | 与速度平方相关的空气阻力 |
| `rollingResistance` | 低速滚动阻力 |
| `maxSteeringAngle` | 低速最大前轮转角 |
| `inputResponse` | 键盘输入渐变速度，范围建议 `0~1` |
| `bodyPitchStrength` | 加减速造成的车身俯仰强度 |
| `bodyRollStrength` | 转弯造成的车身侧倾强度 |

未填写新增字段时，`VehicleConfigLoader` 会使用默认值，因此旧车辆 JSON 仍可加载。

## 添加或替换车辆

1. 将 OBJ 放入 `assets/vehicleframework/models/obj/`。
2. 将 PNG 放入 `assets/vehicleframework/textures/entity/vehicle/`。
3. 在 `data/vehicleframework/vehicles/` 创建车辆配置。
4. 检查模型的轴向、UV、尺寸和原点。
5. 调整车辆物理参数。
6. 构建并重新部署 JAR。

### 当前限制

- 当前注册的 `VehicleRenderer` 仍固定引用 Civic 的模型和贴图资源；完整的按车辆配置动态切换渲染资源尚未接入主渲染器。
- OBJ 中的车轮对象没有可靠的 `wheel_fl`、`wheel_fr`、`wheel_rl`、`wheel_rr` 分组，因此暂未启用车轮转向和滚动动画。
- 碰撞箱仍是 Minecraft 直立 AABB；车身 pitch/roll 只影响视觉和乘员位置。
- JSON 中的座位、碰撞箱和车轮名称结构目前并未全部接入运行时。
- `VehicleKeyItem` 已有基础代码，但完整的点火、锁车和权限流程尚未完成。

如需车轮动画，应先在 Blockbench 中将四只车轮分别放入明确命名的组，再重新导出 OBJ。

## License

MIT
