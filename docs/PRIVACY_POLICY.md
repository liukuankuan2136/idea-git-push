# Issue Link Push 隐私政策

最后更新：2026-08-24

## 处理范围

Issue Link Push 是一个 IntelliJ IDEA 客户端插件。插件作者 LKK 不提供 DevOps 后端服务，也不会通过插件收集广告数据、分析数据或建立独立的用户画像。

插件功能请求发送到固定地址 `https://devops.ctjsoft.com`。这些请求可能包含完成所选操作所需的 DevOps 账号信息、任务/产品/项目数据、工时或日报内容，以及你主动提交的 Git 相关信息。具体数据的接收和处理还受该 DevOps 服务运营方的规则约束。

## 凭据和会话

- DevOps 用户名和登录请求中的 password 密文保存在 IntelliJ IDEA 的 PasswordSafe 中；
- 登录后得到的会话 Cookie 只在运行期间保存在内存中，不写入插件配置文件；
- 插件不会把密码、Cookie、完整 Authorization、完整 user-context 或完整登录响应写入日志。

## 诊断日志

只有在设置中启用“启用脱敏诊断日志”后，插件才会写入诊断日志。日志按脱敏规则记录请求阶段、端点路径、状态码、数量和错误类型等信息；不会记录密码和会话 Cookie。关闭该设置后，插件不写入这些诊断日志。

## 数据保留和删除

插件作者不控制 `devops.ctjsoft.com` 服务端的数据保留策略。你可以在 IntelliJ IDEA 设置中删除插件保存的账号凭据，也可以使用插件提供的缓存清理功能；服务端数据应向对应的 DevOps 服务运营方申请查询、更正或删除。

## 联系方式

隐私问题请联系：liu740721666@gmail.com
