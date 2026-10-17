# 咒术映界 · Jujutsu Realms

Minecraft 咒术猫咪与术式模组。当前 Java 版本为 **0.102.6**，模组 ID 为 `jujutsuvfx`。

| 工程 | 平台 | Java |
| --- | --- | --- |
| `forge-vfx/` | Minecraft 1.20.1 / Forge 47.4.20 起 | 17 |
| `neoforge-vfx/` | Minecraft 1.21.1 / NeoForge 21.1.248 | 21 |
| `netease-vfx/` | 网易基岩版 ModSDK 实验移植 | Python 2 兼容脚本 |
| `models/` | Blockbench 模型源文件，含末地传送门框架 | — |

Java 双端包含五条悟猫、宿傩猫、魔虚罗，苍、赫、茈、解、捌、灶开及领域，猫咪战斗演出、自动运镜、体术与三维咒具游云。网易工程仍是独立实验版本，不与 Java 功能完整等价。

## 构建 Java 双端

在对应工程目录执行，提前将 `JAVA_HOME` 指向所需 JDK。

Forge：

```powershell
cd forge-vfx
.\gradlew.bat --no-daemon build
```

NeoForge：

```powershell
cd neoforge-vfx
.\gradlew.bat --no-daemon build "-PsableJar=C:/dependencies/sable-neoforge-1.21.1-2.0.3.jar"
```

NeoForge 编译需要外部 Sable 2.0.3 JAR，用于可选航空学物理兼容接口。通过 `sableJar` 指定本机路径；构建会提取其内嵌 companion API，但不会把物理引擎打包进本模组。游戏中未安装航空学时，捌使用普通方块破坏路径。

Linux/macOS 使用 `./gradlew`。产物在各工程的 `build/libs/`；`build` 同时运行已有自动检查。

## 配置

进入世界后按 **G** 打开配置。画面与配乐设置保存在客户端；方块破坏、演出模式、术式伤害和领域持续时间由服务器保存，修改需要管理员权限。

两个领域的常规持续时间可分别输入 **5–120 秒**，默认 **5 秒**。新配置影响下一次常规释放；已展开的领域、领域对抗计时和演出固定桥段保持原定节奏。管理员指令可明确指定临时时长。

## 空间斩（0.102.6）

创造物品栏中的“空间斩”，或使用 `/give @s jujutsuvfx:world_cut` 获取。右键摆手后，以出刀时的视线贯穿前方 48 格路径上的多个生物，冷却 2 秒，可穿过地形；本技能不切割方块或物理结构。

服务端直接执行死亡流程，跳过普通伤害、护甲、盾牌、不死图腾，以及本模组猫咪的终结演出。保留队伍、创造/旁观模式和服务器 PvP 保护规则；术式伤害倍率不改变处决效果。末影龙使用原版即时死亡收尾，保留末地进度。

客户端采集命中时的模型顶点、姿势与贴图，按刀痕所在斜面裁切并封闭断面，再将两半弹开、碎散；约 0.8 秒消失。服务器广播给附近装有模组的观察者。Forge 联机双方需使用本版协议。

模型裁切支持标准 `NEW_ENTITY` 四边形渲染通道，包括原版生物、玩家和本模组猫咪；绕过该顶点通道的第三方自定义渲染器需要另行适配。几何裁切、顶点数据保留、路径判定和数据包往返有自动检查；这些检查不等于游戏内画面或联机实测。

## 网易实验工程

工程说明和已知限制见 [netease-vfx/README.md](netease-vfx/README.md)。Python 3 可运行离线检查：

```powershell
cd netease-vfx
python tools/verify.py
python tools/verify_all.py
python tools/verify_charge021.py
```

离线通过不代表网易客户端画面、手机性能或手臂显示已通过实机验证。

本仓库保存源码、运行资源和测试；构建缓存、存档、日志、本地备份及历史安装包不纳入 Git。
