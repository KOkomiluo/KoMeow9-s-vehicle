# 🚗 Vehicle Framework

基于 **NeoForge 1.20.1** 的自定义 OBJ 载具框架 Mod。（参考 [Immersive Vehicles (MTS)](https://github.com/DonBruce64/MinecraftTransportSimulator) 架构）

## 📋 功能特性

- 🏎️ **数据驱动** — 通过 JSON 配置文件定义车辆属性，无需修改代码即可添加新车型
- 🎮 **WASD 驾驶** — 标准键鼠操控，按键状态实时同步至服务端
- ⚙️ **MTS 风格物理引擎** — kinematic 转向 + 抓地力修正 + 重心转移 + 引擎惯性 + 自动变速箱
- 🖼️ **自定义 OBJ 渲染** — 完全自研 OBJ 加载管线，不依赖 GeckoLib/BakedModel
- 📷 **摄像机优化** — 驾驶时动态 FOV 和平滑第三人称跟随
- 🔑 **车辆钥匙** — 启动引擎、锁定车辆
- 🛠️ **创造工具** — 万能载具生成器，一键放置任意车型

## 🛠️ 开发环境

| 组件        | 版本         |
| ----------- | ------------ |
| Minecraft   | 1.20.1       |
| NeoForge    | 47.1.3       |
| Java        | 17           |
| Gradle      | 8.12         |

## 🚀 快速开始

### 前置要求

1. **JDK 17** — 推荐 [Eclipse Temurin](https://adoptium.net/)
2. **VS Code** + Java Extension Pack

### 构建 & 运行

```bash
# 编译
./gradlew build

# 启动客户端（开发模式）
./gradlew runClient

# 启动服务端（开发模式）
./gradlew runServer
```

Windows 下使用 `gradlew.bat` 代替 `./gradlew`。

### 部署

```powershell
cp build/libs/vehicleframework-0.1.0.jar "$env:APPDATA/.minecraft/versions/1.20.1-NeoForge_47.1.106/mods/"
```

## 📁 项目结构

```
my-vehicle-framework/
├── src/main/java/.../vehicleframework/
│   ├── VehicleFramework.java              # @Mod 主入口
│   ├── api/
│   │   └── IVehicleDriveable.java         # 驾驶接口
│   ├── common/
│   │   ├── entity/                        # VehicleEntity, Seat, CollisionBox
│   │   ├── item/                          # 钥匙、生成器、拆卸工具
│   │   ├── network/                       # WASD 按键同步封包
│   │   ├── physics/                       # 物理引擎（MTS 风格）
│   │   │   ├── VehiclePhysicsEngine.java  # 主物理循环
│   │   │   ├── Wheel.java                 # 车轮数据
│   │   │   └── CollisionCalculator.java   # 碰撞检测
│   │   └── registry/                      # DeferredRegister 注册
│   ├── client/
│   │   ├── model/                         # OBJ 模型加载
│   │   │   ├── ObjLoader.java             # OBJ 解析器（MTS 纯净风格）
│   │   │   ├── ObjModelGroup.java         # 按 o/g 分组的模型容器
│   │   │   ├── ObjModel.java              # 单对象模型
│   │   │   ├── ObjFace.java               # 三角面
│   │   │   ├── ObjVertex.java             # 顶点（不可变值类）
│   │   │   └── ObjModelCache.java         # 缓存
│   │   ├── render/                        # 自定义 EntityRenderer
│   │   │   ├── VehicleRenderer.java       # 主渲染器
│   │   │   └── ObjVehicleRenderer.java    # 备用 OBJ 渲染器
│   │   ├── input/                         # 键盘输入监听
│   │   └── camera/                        # 摄像机优化
│   └── data/
│       ├── VehicleType.java               # 车辆配置 record
│       └── VehicleConfigLoader.java       # JSON 加载器
├── src/main/resources/
│   ├── META-INF/neoforge.mods.toml
│   ├── assets/vehicleframework/
│   │   ├── lang/                          # 语言文件 (en_us, zh_cn)
│   │   ├── models/
│   │   │   ├── block/
│   │   │   └── item/
│   │   ├── models/obj/                    # OBJ 模型文件
│   │   │   ├── civic_2008.obj            # Civic 2008（当前使用）
│   │   │   ├── civic2008.obj
│   │   │   └── sports_car.obj
│   │   └── textures/
│   │       ├── entity/vehicle/            # 载具贴图
│   │       ├── block/
│   │       └── misc/                      # white.png 等
│   └── data/vehicleframework/
│       └── vehicles/                      # 车辆 JSON 配置
├── build.gradle
├── gradle.properties
└── settings.gradle
```

## 🖼️ OBJ 渲染管线

```
OBJ 文件 → ObjLoader
              ├── 按 o/g 拆分为 ObjModelGroup
              ├── 扇面三角化（MTS 风格，零翻转）
              ├── V 轴翻转 (v = 1.0 - v)
              ├── 可配置轴翻转 (FLIP_X / FLIP_Z)
              └── 退化第 4 顶点（兼容 MC 四边形缓冲）
                    ↓
              ObjModelCache (ConcurrentHashMap)
                    ↓
              VehicleRenderer
                    ↓
              entityTranslucent + VertexConsumer
                    ↓
              MC Render Pipeline
```

### 坐标轴翻转开关

在 [ObjLoader.java](src/main/java/com/yourname/vehicleframework/client/model/ObjLoader.java) 中调整：

```java
FLIP_X =  1.0f  // 1.0=不翻, -1.0=翻转（修正方向盘左右）
FLIP_Z = -1.0f  // Z 轴翻转（OBJ 右手系→MC 左手系）
```

## ⚙️ 物理引擎（MTS 风格）

参考 MTS 的 `AEntityVehicleD_Moving` 实现：

| 系统 | 实现 |
|------|------|
| **转向** | kinematic 模型：`turningForce = (steerAngle / wheelbase) × speed`，高速指数衰减 |
| **抓地力** | motion 朝 heading 拉回，`skiddingFactor` 控制侧滑程度 |
| **漂移** | 手刹降低后轮抓地力 → motion 不对齐 heading → 自然侧滑 |
| **驱动** | 扭矩曲线 × 齿轮比 × 主减速比 / 轮胎半径 → acceleration |
| **制动** | 速度标量递减 + 手刹增强 |
| **变速箱** | 6 速自动，基于 RPM 升降挡 |
| **引擎** | 惯性模型 + 发动机制动 |
| **悬挂** | 4 轮独立射线检测 + 弹簧-阻尼 |
| **重心转移** | 加减速/过弯重量分配影响抓地力 |

## 🚗 添加新车型

1. 将 `.obj` 模型放入 `assets/vehicleframework/models/obj/`
2. 将 `.png` 贴图放入 `assets/vehicleframework/textures/entity/vehicle/`
3. 在 `data/vehicleframework/vehicles/` 下创建 JSON 配置文件
4. 在 [VehicleRenderer.java](src/main/java/com/yourname/vehicleframework/client/render/VehicleRenderer.java) 更新 `MODEL_LOCATION` 和 `TEXTURE_LOCATION`
5. 视需要调整 `YAW_OFFSET`、`FLIP_X`、`FLIP_Z`
6. `./gradlew build` 并复制 jar，重启游戏

### JSON 配置示例

```json
{
  "id": "sports_car",
  "displayName": "Sports Car",
  "maxSpeed": 1.0,
  "weight": 1500.0,
  "fuelCapacity": 100.0,
  "objModelPath": "models/obj/civic_2008.obj",
  "objScale": 1.0,
  "tirePeakFriction": 1.0,
  "tireSlidingFriction": 0.7,
  "cogHeight": 0.5,
  "engineInertia": 0.15,
  "driveType": "rwd"
}
```

## 📄 License

MIT
