# 🚗 Vehicle Framework

基于 **NeoForge** + **GeckoLib** 的数据驱动载具框架 Mod。

## 📋 功能特性

- 🏎️ **数据驱动** — 通过 JSON 配置文件定义车辆属性，无需修改代码即可添加新车型
- 🎮 **WASD 驾驶** — 标准键鼠操控，按键状态实时同步至服务端
- ⚙️ **物理引擎** — 包含加速、刹车、摩擦力、转向和碰撞计算
- 🎬 **GeckoLib 动画** — 骨骼动画驱动轮子转动、车门开闭等效果
- 📷 **摄像机优化** — 驾驶时动态 FOV 和平滑第三人称跟随
- 🔑 **车辆钥匙** — 启动引擎、锁定车辆
- 🛠️ **创造工具** — 万能载具生成器，一键放置任意车型

## 🛠️ 开发环境

| 组件        | 版本         |
| ----------- | ------------ |
| Minecraft   | 1.20.1       |
| NeoForge    | 47.1.3       |
| GeckoLib    | 4.4.9        |
| Java        | 17           |
| Gradle      | 8.12         |

## 🚀 快速开始

### 前置要求

1. **JDK 17** — 推荐 [Eclipse Temurin](https://adoptium.net/)
2. **VS Code** + Java Extension Pack（项目已推荐安装）

### 初始化 Gradle Wrapper

首次克隆项目后，在项目根目录执行：

```bash
# Windows (PowerShell):
gradle wrapper --gradle-version 8.12

# 如果没有安装 Gradle CLI，可以使用以下方法：
# 1. 下载 Gradle: https://gradle.org/install/
# 2. 或使用 SDKMAN: sdk install gradle
```

### 构建 & 运行

```bash
# 编译
./gradlew build

# 启动客户端（开发模式）
./gradlew runClient

# 启动服务端（开发模式）
./gradlew runServer

# 运行数据生成器
./gradlew runData
```

### Windows 注意事项

Windows 下请使用 `gradlew.bat` 代替 `./gradlew`：

```powershell
gradlew.bat build
gradlew.bat runClient
```

## 📁 项目结构

```
my-vehicle-framework/
├── src/main/java/.../vehicleframework/
│   ├── VehicleFramework.java          # Mod 主入口
│   ├── api/                           # 公开 API 接口
│   ├── common/
│   │   ├── entity/                    # 车辆、座椅、碰撞箱实体
│   │   ├── item/                      # 钥匙、生成器等物品
│   │   ├── network/                   # WASD 按键同步封包
│   │   ├── physics/                   # 物理引擎
│   │   └── registry/                  # DeferredRegister 注册
│   ├── client/
│   │   ├── render/                    # GeckoLib 渲染器
│   │   ├── input/                     # 键盘输入监听
│   │   └── camera/                    # 摄像机优化
│   └── data/                          # JSON 配置加载
├── src/main/resources/
│   ├── META-INF/neoforge.mods.toml    # Mod 元数据
│   ├── assets/vehicleframework/
│   │   ├── lang/                      # 语言文件 (en_us, zh_cn)
│   │   ├── geo/                       # GeckoLib 骨骼模型
│   │   └── animations/                # GeckoLib 动画
│   └── data/vehicleframework/
│       └── vehicles/                  # 车辆配置 JSON
├── build.gradle                       # 构建脚本
├── gradle.properties                  # 版本配置
└── settings.gradle                    # 项目设置
```

## 🚗 添加新车型

1. 在 `data/vehicleframework/vehicles/` 下创建新的 JSON 文件
2. 参考 `sports_car.json` 填写车辆属性
3. 在 `assets/vehicleframework/geo/` 放入 GeckoLib 模型
4. 在 `assets/vehicleframework/animations/` 放入动画文件
5. 在 `assets/vehicleframework/textures/` 放入贴图
6. 重启游戏或执行重载命令即可生效

## 📄 License

MIT
