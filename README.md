# 学生成绩管理系统

本仓库依据 [完整开发规格](docs/开发文档.md) 独立实现。当前阶段、实际验证和未完成工作见 [docs/TASKS.md](docs/TASKS.md)。当前为可运行的身份与教学数据首阶段，不代表完整系统验收完成。

## 本地环境

- Java 23、Maven 3.9+
- Node.js 与 npm
- MySQL 9.x，数据库名 `stu_system`
- Redis 服务可用；当前首阶段尚未接入 Redis 行为

后端默认使用 `local` profile，从未跟踪的根目录 `application-local.yml` 读取数据库和 JWT 配置。不存在时可从 `application-local.example.yml` 建立本机文件，并填入本地数据库连接；不要提交本地密码或密钥。COS 凭据尚未配置。

## 本地启动

先在本机 MySQL 创建数据库：

```sql
CREATE DATABASE stu_system CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci;
```

Flyway 在后端启动时应用 `src/main/resources/db/migration/` 下的迁移。首次使用前，以环境变量提供管理员资料和符合规则的临时口令，再执行初始化：

```powershell
$env:ADMIN_NO = "your-admin-number"
$env:ADMIN_USERNAME = "your-admin-username"
$env:ADMIN_NAME = "Administrator"
$env:ADMIN_PASSWORD = "your-compliant-password"
mvn -q -DskipTests package
java -jar target/stu-system-0.1.0.jar init-admin
Remove-Item Env:ADMIN_NO, Env:ADMIN_USERNAME, Env:ADMIN_NAME, Env:ADMIN_PASSWORD
```

已有管理员时初始化会拒绝覆盖。随后在两个终端运行：

```powershell
mvn spring-boot:run
```

```powershell
cd frontend
npm install
npm run dev
```

Vite development proxy defaults to `http://localhost:8080`. To exercise a separate local API instance, set `STU_SYSTEM_API_PROXY_TARGET` before starting Vite; the override is development-only and is not required for the default setup:

```powershell
$env:STU_SYSTEM_API_PROXY_TARGET = "http://127.0.0.1:8189"
npm run dev -- --port 5174
Remove-Item Env:STU_SYSTEM_API_PROXY_TARGET
```

如果唯一的启用管理员丢失登录凭据，可在数据库已完成迁移且确认使用 `local` profile 时执行本地恢复。该命令只在恰有一个启用管理员时工作，不创建或覆盖账户；成功后递增该账户的 `auth_version` 并记录审计事件。恢复口令通过临时环境变量传入，不保存到项目文件：

```powershell
$env:ADMIN_RECOVERY_PASSWORD = "your-compliant-password"
java -jar target/stu-system-0.1.0.jar recover-admin
Remove-Item Env:ADMIN_RECOVERY_PASSWORD
```

前端地址为 `http://127.0.0.1:5173/`，后端 API 为 `http://localhost:8080/api/v1`，健康检查为 `/actuator/health`，OpenAPI 为 `/v3/api-docs`。端点和当前功能边界见 [docs/API.md](docs/API.md)。
