# LogCat

面向 64 位 Android 的新式 logcat 查看工具,使用 Kotlin + Jetpack Compose 编写。

## 功能

- 底部导航:日志 / 崩溃 / 设置三页平级切换(淡入淡出,无层级暗示)
- 三种日志读取模式
  - **默认模式**:无需授权,通过 `--pid <自身进程>` 仅读取本应用日志
  - **Shizuku**:基于 Shizuku / Sui 授权,以 shell 身份读取整机日志(`dev.rikka.shizuku`)
  - **Root**:通过 `su -c` 提权读取整机日志
- 级别过滤(V/D/I/W/E/F)、缓冲区切换(全部 / main / system / crash)、关键字过滤
- 行密度可切换:紧凑(默认,单行 + 点击展开)/ 舒适(两行式)
- 批量刷新(约 120ms 聚合)+ 环形缓冲(行数可配置,默认 3000)
- 自动滚动以「最后一条日志序号」为触发依据,环形缓冲写满后仍持续跟随
- 时间戳 / PID / TID / 自动换行 / 自动滚动开关
- 长日志续行合并(堆栈保持完整),单条超长截断保护
- 崩溃页:捕获本应用未处理异常,并自动沉淀 logcat 中的 `FATAL EXCEPTION` 段落
- 一键复制、导出、清空(本地缓冲 / 设备缓冲分列)
- 崩溃记录自动去重:同一次崩溃的多行 FATAL 只记一条

## 构建

本项目只通过 GitHub Actions 构建,不在本地构建。

- push 到 `main`:跑单元测试 → 产出 debug + release APK(Artifacts → `logcat-apks`)
- 推送 `v*` tag:自动创建 GitHub Release 并附带 APK

单元测试位于 `app/src/test/`,覆盖 `LogcatParser` 与 `LogcatCommand`(纯 JVM,不依赖 Android):

```bash
./gradlew testDebugUnitTest
```

可选签名 Secrets(未配置时 release 使用 debug 签名):

| Secret | 说明 |
| --- | --- |
| `SIGNING_KEYSTORE_BASE64` | keystore 文件的 base64 |
| `SIGNING_STORE_PASSWORD` | keystore 密码 |
| `SIGNING_KEY_ALIAS` | key alias |
| `SIGNING_KEY_PASSWORD` | key 密码 |

## 结构

```
app/src/main/kotlin/com/solosu/logcat/
├── MainActivity.kt            入口
├── LogCatApplication.kt       依赖容器
├── data/                      设置持久化、日志通道、应用内日志
├── log/                       日志模型、threadtime 解析、命令构造、数据源
├── shell/                     三种模式的进程创建、Shizuku 授权封装
├── crash/                     崩溃记录与全局异常捕获
└── ui/
    ├── LogCatApp.kt           顶层路由与底部导航
    ├── logcat/                日志页
    ├── crash/                 崩溃页
    ├── settings/              设置页
    ├── components/            共用组件(SwitchRow / SettingRow / EmptyState / Tag)
    ├── icons/                 自绘矢量图标(不引入 material-icons-extended)
    └── theme/                 色板与深浅色方案
```

## 说明

- 默认模式受系统限制,无法读取其他应用日志,这是 Android 的权限边界,不是实现缺陷。
- Shizuku 的远程进程通过 `Shizuku.newProcess` 反射调用(该 API 在 13.x 未公开)。
- 需要 Android 8.0+(API 26)。
