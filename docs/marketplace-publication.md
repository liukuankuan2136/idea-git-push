# JetBrains Marketplace 发布准备清单

本文档记录 `Issue Link Push` 提交 JetBrains Marketplace 前的资料和操作项。

## 已补充到工程

- `build.gradle.kts` 已配置 `publishPlugin` 所需的 Marketplace token Provider。
- `build.gradle.kts` 已配置 `signPlugin` 所需的证书链、私钥和私钥密码 Provider。
- `plugin.xml` 已补充英文 `change-notes`。
- 发布密钥只从环境变量或 Gradle Project Property 读取，不提交到仓库。

## 需要提供或确认的资料

- [x] Vendor 展示名称：`LKK`。
- [x] Vendor/项目主页：`https://github.com/liukuankuan2136/idea-git-push`。
- [x] 发布联系邮箱：`liu740721666@gmail.com`。
- [x] 插件 Logo：由 `D:\code\my-workspace\git-push\icon.png` 整理为 `src/main/resources/META-INF/pluginIcon.svg`。
- [ ] EULA URL：推送到 `main` 后使用 `https://github.com/liukuankuan2136/idea-git-push/blob/main/docs/EULA.md`。
- [ ] Privacy Policy URL：推送到 `main` 后使用 `https://github.com/liukuankuan2136/idea-git-push/blob/main/docs/PRIVACY_POLICY.md`。
- [x] 发布范围：公开发布；使用功能需要 `devops.ctjsoft.com` 的有效账号和权限。
- [x] 兼容范围：从 IDEA 2024.3（build 243）起支持，不设置 `until-build`，以保留后续版本兼容空间；当前已验证 IDEA 2026.1。
- [ ] JetBrains Marketplace Vendor 账号。
- [ ] Marketplace Personal Access Token。
- [ ] Marketplace 插件签名证书链、私钥和私钥密码。

## 当前需要特别确认的产品边界

插件当前固定连接 `https://devops.ctjsoft.com`，并要求用户配置该服务的账号。公开发布前，需要把 `docs/EULA.md` 和 `docs/PRIVACY_POLICY.md` 推送到 GitHub，并在 Marketplace 页面填写对应的公开 URL。

## 发布凭据环境变量

不要把真实值写入 `gradle.properties`、源码或 Git。发布时在当前 PowerShell 会话中提供：

```powershell
$env:ORG_GRADLE_PROJECT_intellijPlatformPublishingToken = "<Marketplace token>"
$env:CERTIFICATE_CHAIN = "<base64 encoded certificate chain>"
$env:PRIVATE_KEY = "<base64 encoded private key>"
$env:PRIVATE_KEY_PASSWORD = "<private key password>"
```

首次发布需要登录 Marketplace 后手动上传；首次上传成功后，后续版本才使用：

```powershell
.\gradlew.bat clean test buildPlugin verifyPluginProjectConfiguration verifyPlugin publishPlugin --no-daemon
```

发布前必须先确认 ZIP 已在全新目标 IDEA 实例中安装并完成关键流程冒烟测试。
