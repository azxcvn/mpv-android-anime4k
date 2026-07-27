# WebDAV 使用说明

## 快速开始

### 1. 进入 WebDAV 管理

主页右下角点击 **FAB（+ 按钮）展开** → 选择 **WebDAV** → 进入管理界面

### 2. 添加账户

点击**添加账户**，填写以下信息：

- **显示名称**：自定义名称，如"我的网盘"
- **主机地址**：服务器 IP 或域名，如 `dav.jianguoyun.com`
- **端口**：服务端口号（留空则使用默认 80/443）
- **路径**：WebDAV 根路径，如 `/dav/`（注意首尾的 `/`）
- 填写后下方会**自动预览完整地址**，确认无误即可

**选项（分割线下方）**：
- ☑ **匿名访客访问**：勾选后无需账号密码即可连接
- ☑ **HTTPS**：勾选启用加密连接（坚果云等建议勾选）

- **账号 / 密码**：WebDAV 凭证（坚果云需使用**应用密码**），匿名模式下隐藏

点击**测试连接** → 看到绿色 ✓ 图标即成功 → **保存**

### 3. 浏览与播放

点击已保存的账户 → 文件列表分为**文件夹**、**视频**、**其他文件**三个区域，每个视频下方显示文件大小和修改日期标签。点击视频即可播放。

---

## 技术实现

### 核心组件

| 组件 | 用途 |
|------|------|
| Sardine (OkHttpSardine) | WebDAV 协议客户端，文件浏览/PROPFIND |
| OkHttp | 底层 HTTP 请求，代理内 Range/Seek |
| NanoHTTPD | 本地 HTTP 代理服务器，桥接 mpv 与远程 WebDAV |
| EncryptedSharedPreferences | 凭证加密存储 |
| Jetpack Compose | UI 实现 |

### 播放架构

```
mpv → http://127.0.0.1:PORT/streamId（本地代理）
        ↓ 代理内部用 OkHttp + Authorization 头
        → http://host:port/path/video.mkv（远程 WebDAV）
```

- 本地代理自动处理 Seek 时的 `Range` 请求
- 认证凭据仅存在于代理内部，不会嵌入到播放 URL 中
- 字幕同样通过代理流加载

### 代码架构

```
webdav/
├── domain/webdav/
│   ├── WebDavClient.kt             # Sardine 封装（浏览/PROPFIND）
│   ├── WebDavStreamingProxy.kt     # NanoHTTPD 本地代理服务器
│   └── WebDavConfig.kt             # 运行时配置
├── data/
│   ├── model/WebDavAccount.kt      # 账户数据模型
│   └── preferences/WebDavAccountDataSource.kt  # 加密存储
├── repository/WebDavRepository.kt  # 仓储层
├── presentation/WebDavViewModel.kt # ViewModel
└── ui/webdav/WebDavScreen.kt       # Compose UI
```

---

## 常见问题

**Q：连接失败？**
检查：主机地址是否正确、端口是否匹配、路径是否以 `/` 开头和结尾、账号密码是否有误

**Q：坚果云密码？**
使用**第三方应用密码**（在坚果云账户安全设置中生成），非登录密码

**Q：HTTPS 连接失败？**
部分自建 WebDAV 服务使用自签名证书，可尝试关闭 HTTPS 开关使用 HTTP 连接

**Q：拖进度条卡顿或失败？**
已通过本地代理架构解决。如仍有问题，请提供 `tag:WebDavStreamingProxy` 的日志

