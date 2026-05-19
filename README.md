# InkPad

轻量 Markdown 编辑器，专为安卓墨水屏阅读器设计。

## 特性

- **极速启动**：原生 Android，无 WebView，无插件体系
- **行级 MD 渲染**：仅支持标题、加粗、高亮、删除线、引用、callout，击键只刷新当前行
- **无动画**：所有过渡效果已禁用，对墨水屏友好
- **←/→ 导航**：顶部按钮移动光标到上/下行，不触发全屏滚动
- **侧边栏文件列表**：抽屉式，可折叠
- **底部工具栏**：加粗、高亮、删除线、删行、清格式、缩进、取消缩进、选行、清空行、沉浸模式
- **阅读进度条 + 字数统计**
- **WebDAV 同步**：三种模式
  - 增量上传（带删除）
  - 增量下载（带删除）
  - 双向增量同步

## 获取 APK

### 方式一：GitHub Actions（推荐，无需本地环境）

1. Fork 本仓库到你的 GitHub 账号
2. 进入你的 repo → Actions → Build APK
3. 点击 "Run workflow"
4. 等待约 5-10 分钟
5. 下载 Artifacts 中的 `InkPad-release.apk`

### 方式二：本地编译

```bash
# 需要 JDK 17 + Android SDK + Gradle 8.4
gradle assembleRelease
# APK 在 app/build/outputs/apk/release/
```

## 安装

1. 安卓设备 → 设置 → 安全 → 允许未知来源
2. 用文件管理器打开 APK 安装

## 使用

- **打开文件列表**：右上角菜单图标，或从左边缘向右滑动
- **新建文件**：文件列表右上角 +
- **同步**：文件列表底部「同步」按钮
- **配置 WebDAV**：同步菜单 → 配置 WebDAV

## WebDAV 兼容性

测试过：Nextcloud、Infinicloud（teracloud）、坚果云（需用应用密码）

## 笔记存储位置

默认：`/sdcard/InkPad/`

可在设置中更改。

## Callout 语法

```
> [!note] 这是一个提示
> [!warning] 这是警告
> [!tip] 小技巧
> [!important] 重要
```
