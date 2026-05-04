# ShellBox 📦

一个现代、简洁的 Android SSH 客户端。蓝色主题，白底设计，支持多 Tab 终端会话。

<p align="center">
  <img src="docs/screenshot.png" width="320" alt="ShellBox Screenshot"/>
</p>

## ✨ 功能特性

- **快速连接** — 直接输入 IP / 端口 / 用户名 / 密码或私钥即可连接
- **保存服务器** — 保存常用服务器配置，一键点击连接
- **多 Tab 会话** — 同时管理多个 SSH 连接，Tab 切换自如
- **虚拟键盘** — 两排虚拟按键，支持 `CTRL`、`ALT`、`ESC`、`TAB`、方向键、`|`、`~` 等
- **CTRL 组合键** — 点亮 CTRL 键后用输入法输入字母，模拟 Ctrl+C / Ctrl+Z 等
- **长按复制** — 长按终端内容可选中并复制
- **双重认证** — 支持密码认证和私钥认证（支持密钥密码）
- **白底黑字终端** — 清晰易读的终端配色

## 🏗️ 构建

### 前置条件
- Android Studio Hedgehog 或更高版本
- JDK 17
- Android SDK (API 26+)

### 本地构建
```bash
git clone https://github.com/YOUR_USERNAME/ShellBox.git
cd ShellBox
./gradlew assembleRelease
```

### GitHub Actions 自动构建

每次推送到 `main` 分支自动编译，打 `v*` tag 自动发布 Release。

#### 配置签名（在 GitHub Secrets 中添加）

| Secret 名称 | 说明 |
|-------------|------|
| `KEYSTORE_BASE64` | JKS 密钥库文件的 Base64 编码 |
| `KEYSTORE_PASSWORD` | 密钥库密码 |
| `KEY_ALIAS` | 密钥别名 |
| `KEY_PASSWORD` | 密钥密码 |

#### 生成签名密钥

```bash
# 生成新密钥库
keytool -genkeypair -v \
  -keystore shellbox-release.jks \
  -keyalg RSA -keysize 2048 \
  -validity 10000 \
  -alias shellbox

# 转换为 Base64（粘贴到 KEYSTORE_BASE64 secret）
base64 -w 0 shellbox-release.jks
```

#### 发布新版本
```bash
git tag v1.0.0
git push origin v1.0.0
```

## 🛠️ 技术栈

| 模块 | 技术选型 |
|------|---------|
| 语言 | Kotlin |
| UI | Jetpack Compose + Material 3 |
| SSH | SSHJ (com.hierynomus:sshj) |
| 数据库 | Room |
| DI | Hilt |
| 导航 | Navigation Compose |

## 📁 项目结构

```
app/src/main/java/com/shellbox/
├── data/
│   ├── db/          # Room 数据库 & DAO
│   ├── model/       # 数据类（Server, QuickConnect）
│   └── repository/  # 数据仓库层
├── di/              # Hilt 依赖注入模块
├── ssh/             # SSH 连接管理（SshManager, SshSession）
├── ui/
│   ├── addserver/   # 添加/编辑服务器页面
│   ├── home/        # 主页（快速连接 + 服务器列表）
│   ├── terminal/    # 终端页面 + 虚拟键盘
│   └── theme/       # Material 3 主题（蓝色系）
├── MainActivity.kt  # 入口 + 导航图
└── ShellBoxApp.kt   # Application 类
```

## 📄 License

MIT License
