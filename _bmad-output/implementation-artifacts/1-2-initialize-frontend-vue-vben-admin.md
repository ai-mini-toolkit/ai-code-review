# Story 1.2: 从 Vue-Vben-Admin 模板初始化前端项目

**Status:** ready-for-dev

**Epic:** 1 - 项目基础设施与配置管理 (Project Infrastructure & Configuration Management)

---

## 📋 Story 概述

**用户故事:**
```
As a 前端开发者,
I want to 从 Vue-Vben-Admin 模板创建前端项目,
So that 我可以建立现代化的管理界面基础。
```

**业务价值:**
此故事建立了 AI 代码审查系统的前端基础架构。Vue-Vben-Admin 5.0 提供了企业级的 Vue 3 管理模板，包含完整的路由、状态管理、UI 组件和 API 客户端模式，可以显著加速后续前端开发（Epic 8 的所有 Stories）。

**Story ID:** 1.2
**Priority:** HIGH - Epic 1 的第二个关键基础故事
**Complexity:** Medium
**Dependencies:** Story 1.1 (后端项目已初始化，提供 API 端点基础)

---

## ✅ Acceptance Criteria (验收标准)

**Given** 项目根目录的 frontend/ 目录为空
**When** 执行 Vue-Vben-Admin 5.0 初始化
**Then** 创建以下目录结构：

### 核心目录结构
- `src/views/` - 页面组件（按功能模块组织）
- `src/components/` - 可复用 UI 组件
- `src/api/` - API 客户端模块（Axios 封装）
- `src/stores/` - Pinia 状态管理 store
- `src/router/` - Vue Router 路由配置
- `src/utils/` - 工具函数和帮助方法
- `src/types/` - TypeScript 类型定义
- `src/layouts/` - 布局组件（来自 Vue-Vben-Admin）

**And** 配置文件包括：
- `vite.config.ts` - Vite 构建配置
- `tsconfig.json` - TypeScript 编译配置
- `package.json` - 项目元数据和依赖声明
- `.env.development` - 开发环境变量
- `.env.production` - 生产环境变量

**And** 已安装核心依赖：
- Vue 3 (latest)
- Vite 5.x
- TypeScript 5.x
- Element Plus (UI 组件库)
- Pinia (状态管理)
- Vue Router 4 (路由)
- Axios (HTTP 客户端)
- Shadcn UI + Tailwind CSS (来自 Vben 5.0)

**And** 项目成功启动：
- `pnpm dev` 启动开发服务器（默认端口 5173）
- 浏览器可访问 http://localhost:5173
- 页面无控制台错误
- 热模块替换 (HMR) 正常工作

**And** 项目成功构建：
- `pnpm build` 生成生产构建
- 构建输出到 `dist/` 目录
- 无构建错误或警告
- 生成的文件经过代码分割和优化

---

## 🎯 Tasks / Subtasks (任务分解)

### Task 1: 环境准备和工具安装 (AC: 配置文件)
- [ ] 验证 Node.js 版本 >= 20.15.0
- [ ] 全局安装 corepack: `npm i -g corepack`
- [ ] 启用 corepack: `corepack enable`
- [ ] 确认 frontend/ 目录存在（如不存在则创建）

### Task 2: 克隆 Vue-Vben-Admin 5.0 模板 (AC: 目录结构)
- [ ] 克隆仓库: `git clone https://github.com/vbenjs/vue-vben-admin.git frontend`
- [ ] 进入项目目录: `cd frontend`
- [ ] 检出最新 5.0 版本（如果 main 分支不是 5.0）
- [ ] 删除 .git 目录（可选，如果想重新初始化 git）

### Task 3: 安装项目依赖 (AC: 已安装核心依赖)
- [ ] 执行 `pnpm install`（corepack 会自动使用项目指定的 pnpm 版本）
- [ ] 如果安装失败，执行 `pnpm run reinstall` 重试
- [ ] 验证 node_modules/ 目录已创建
- [ ] 验证 pnpm-lock.yaml 已生成

### Task 4: 配置环境变量 (AC: 配置文件)
- [ ] 复制 `.env.example` 到 `.env.development`
- [ ] 配置开发环境 API Base URL:
  ```
  VITE_API_BASE_URL=http://localhost:8080
  ```
- [ ] 复制 `.env.example` 到 `.env.production`
- [ ] 配置生产环境 API Base URL（占位符）:
  ```
  VITE_API_BASE_URL=https://api.aicodereview.example.com
  ```

### Task 5: 调整项目配置 (AC: 配置文件)
- [ ] 检查 `vite.config.ts` - 确认端口配置为 5173
- [ ] 检查 `tsconfig.json` - 确认 strict mode 启用
- [ ] 检查 `package.json` - 更新 `name` 字段为 `ai-code-review-frontend`
- [ ] 检查 `package.json` - 确认 scripts 包含 `dev`, `build`, `preview`

### Task 6: 启动开发服务器验证 (AC: 项目成功启动)
- [ ] 执行 `pnpm dev`
- [ ] 验证控制台输出显示 "Local: http://localhost:5173"
- [ ] 在浏览器访问 http://localhost:5173
- [ ] 验证 Vben Admin 登录页面正常显示
- [ ] 检查浏览器控制台无错误
- [ ] 测试热模块替换 (修改组件文件，验证自动刷新)

### Task 7: 执行生产构建验证 (AC: 项目成功构建)
- [ ] 执行 `pnpm build`
- [ ] 验证构建成功完成（无错误）
- [ ] 检查 `dist/` 目录已生成
- [ ] 验证 `dist/index.html` 存在
- [ ] 验证 `dist/assets/` 包含 JS 和 CSS 文件
- [ ] 执行 `pnpm preview` 预览生产构建（可选）

### Task 8: 配置 CORS 和 API 代理（开发环境）(AC: API 通信)
- [ ] 在 `vite.config.ts` 配置开发代理:
  ```typescript
  server: {
    proxy: {
      '/api': {
        target: 'http://localhost:8080',
        changeOrigin: true,
      }
    }
  }
  ```
- [ ] 测试代理配置（可通过浏览器访问 /api/actuator/health）

---

## 💻 Dev Notes (开发注意事项)

### Vue-Vben-Admin 5.0 重要变更

**⚠️ 版本兼容性警告:**
- **Vue-Vben-Admin 5.0 与之前版本不兼容**
- 如果团队成员克隆旧版本，会遇到 API 差异
- 确保所有开发者使用 5.0 分支

**新技术栈 (5.0):**
- **UI 框架:** Shadcn UI + Tailwind CSS（替代了旧版的 Ant Design Vue）
- **架构:** Monorepo (pnpm + turborepo)
- **构建工具:** Vite 5.x
- **TypeScript:** 5.x

### 必需的环境要求

| 组件 | 版本/规范 | 原因 |
|------|----------|------|
| Node.js | **20.15.0+** | Vue-Vben-Admin 5.0 要求 |
| pnpm | **项目指定版本** (通过 corepack) | 唯一支持的包管理器 |
| corepack | **Latest** | 自动管理 pnpm 版本 |

**⚠️ 不要使用 npm 或 yarn:**
- Vue-Vben-Admin 5.0 只支持 pnpm
- 使用其他包管理器会导致依赖安装失败

### 项目目录结构（初始化后）

```
frontend/
├── public/                        # 静态资源（不经过 Vite 处理）
├── src/
│   ├── api/                       # API 客户端模块
│   │   ├── request.ts             # Axios 封装（拦截器配置）
│   │   ├── types.ts               # API 类型定义
│   │   └── modules/               # 按功能拆分的 API
│   │       ├── auth.ts
│   │       ├── project.ts
│   │       └── review.ts
│   ├── assets/                    # 静态资源（经过 Vite 处理）
│   ├── components/                # 可复用组件
│   │   ├── common/                # 通用组件
│   │   └── business/              # 业务组件
│   ├── composables/               # Vue 3 Composition API 可复用逻辑
│   ├── layouts/                   # 布局组件（Vben 提供）
│   ├── router/                    # Vue Router 配置
│   │   ├── index.ts               # 路由实例
│   │   └── routes/                # 路由定义
│   ├── stores/                    # Pinia 状态管理
│   │   ├── index.ts               # Store 入口
│   │   └── modules/               # 按功能拆分的 Store
│   │       ├── auth.ts
│   │       ├── project.ts
│   │       └── review.ts
│   ├── styles/                    # 全局样式
│   ├── types/                     # TypeScript 类型定义
│   ├── utils/                     # 工具函数
│   ├── views/                     # 页面组件
│   │   ├── dashboard/             # 仪表板
│   │   ├── project/               # 项目管理
│   │   ├── review/                # 审查历史
│   │   ├── config/                # 配置管理
│   │   └── login/                 # 登录页
│   ├── App.vue                    # 根组件
│   └── main.ts                    # 应用入口
├── .env.development               # 开发环境变量
├── .env.production                # 生产环境变量
├── index.html                     # HTML 入口
├── package.json                   # 项目配置
├── pnpm-lock.yaml                 # 依赖锁文件
├── tsconfig.json                  # TypeScript 配置
└── vite.config.ts                 # Vite 配置
```

### 命名约定（前端）

**组件命名:**
- 文件名: PascalCase (例: `ProjectList.vue`, `ReviewDetail.vue`)
- 组件注册名: PascalCase (例: `<ProjectList />`)
- 布局组件: 前缀 `Layout` (例: `LayoutDefault.vue`)
- 业务组件: 描述性名称 (例: `IssueCard.vue`, `CallGraphViewer.vue`)

**API 模块命名:**
- 文件名: camelCase (例: `project.ts`, `reviewTask.ts`)
- 函数名: camelCase (例: `getProjects()`, `createReviewTask()`)

**Store 命名:**
- 文件名: camelCase (例: `auth.ts`, `project.ts`)
- Store ID: camelCase (例: `defineStore('auth', ...)`)
- Composable: use 前缀 (例: `useAuthStore()`)

**类型定义:**
- Interface: PascalCase (例: `Project`, `ReviewTask`)
- Type Alias: PascalCase (例: `ApiResponse<T>`)
- Enum: PascalCase (例: `TaskStatus`)

### 关键配置文件详解

#### 1. vite.config.ts - Vite 配置

```typescript
import { defineConfig } from 'vite'
import vue from '@vitejs/plugin-vue'
import path from 'path'

export default defineConfig({
  plugins: [vue()],

  resolve: {
    alias: {
      '@': path.resolve(__dirname, 'src'),
      '#': path.resolve(__dirname, 'types'),
    },
  },

  server: {
    port: 5173,
    host: true,  // 监听所有地址（Docker 需要）
    proxy: {
      '/api': {
        target: 'http://localhost:8080',  // 后端 API 地址
        changeOrigin: true,
      },
    },
  },

  build: {
    target: 'es2020',
    outDir: 'dist',
    sourcemap: false,  // 生产环境禁用 sourcemap
    rollupOptions: {
      output: {
        manualChunks: {
          'vue-vendor': ['vue', 'vue-router', 'pinia'],
          'ui-vendor': ['element-plus'],
        },
      },
    },
  },
})
```

**配置要点:**
- `alias`: 路径别名（`@` 指向 `src/`, `#` 指向 `types/`）
- `server.proxy`: 开发环境 API 代理（解决 CORS 问题）
- `build.rollupOptions.manualChunks`: 代码分割策略（减少首屏加载时间）

#### 2. tsconfig.json - TypeScript 配置

```json
{
  "compilerOptions": {
    "target": "ES2020",
    "useDefineForClassFields": true,
    "module": "ESNext",
    "lib": ["ES2020", "DOM", "DOM.Iterable"],
    "skipLibCheck": true,

    "moduleResolution": "bundler",
    "allowImportingTsExtensions": true,
    "resolveJsonModule": true,
    "isolatedModules": true,
    "noEmit": true,
    "jsx": "preserve",

    "strict": true,
    "noUnusedLocals": true,
    "noUnusedParameters": true,
    "noFallthroughCasesInSwitch": true,

    "baseUrl": ".",
    "paths": {
      "@/*": ["src/*"],
      "#/*": ["types/*"]
    }
  },
  "include": ["src/**/*.ts", "src/**/*.d.ts", "src/**/*.tsx", "src/**/*.vue"],
  "references": [{ "path": "./tsconfig.node.json" }]
}
```

**配置要点:**
- `strict: true`: 启用所有严格类型检查
- `paths`: 路径映射（与 vite.config.ts 的 alias 对应）
- `noUnusedLocals/Parameters`: 防止未使用的变量（提高代码质量）

#### 3. .env.development - 开发环境变量

```bash
# 开发环境配置
NODE_ENV=development

# API 基础地址（通过 Vite 代理）
VITE_API_BASE_URL=/api

# 是否启用 Mock 数据（可选）
VITE_USE_MOCK=false

# 应用标题
VITE_APP_TITLE=AI Code Review - Dev

# 是否显示调试信息
VITE_SHOW_DEBUG_INFO=true
```

#### 4. .env.production - 生产环境变量

```bash
# 生产环境配置
NODE_ENV=production

# API 基础地址（实际生产域名）
VITE_API_BASE_URL=https://api.aicodereview.example.com

# 是否启用 Mock 数据
VITE_USE_MOCK=false

# 应用标题
VITE_APP_TITLE=AI Code Review

# 是否显示调试信息
VITE_SHOW_DEBUG_INFO=false
```

### API 客户端模式（Axios 封装）

**utils/request.ts - 标准化的 Axios 实例:**

```typescript
import axios, { AxiosInstance, AxiosRequestConfig, AxiosResponse } from 'axios'
import { ElMessage } from 'element-plus'
import { useAuthStore } from '@/stores/modules/auth'

// API 响应标准格式（与后端 ApiResponse<T> 对应）
export interface ApiResponse<T = any> {
  success: boolean
  data: T | null
  error: ErrorDetail | null
  timestamp: string
}

export interface ErrorDetail {
  code: string
  message: string
  details?: any
}

// 创建 Axios 实例
const service: AxiosInstance = axios.create({
  baseURL: import.meta.env.VITE_API_BASE_URL,
  timeout: 30000,
  headers: {
    'Content-Type': 'application/json',
  },
})

// 请求拦截器：添加 JWT Token
service.interceptors.request.use(
  (config) => {
    const authStore = useAuthStore()
    const token = authStore.token

    if (token) {
      config.headers.Authorization = `Bearer ${token}`
    }

    return config
  },
  (error) => {
    return Promise.reject(error)
  }
)

// 响应拦截器：统一错误处理
service.interceptors.response.use(
  (response: AxiosResponse<ApiResponse>) => {
    const { data } = response

    // 后端返回 success: true
    if (data.success) {
      return data.data  // 直接返回 data 字段
    }

    // 后端返回 success: false
    ElMessage.error(data.error?.message || '请求失败')
    return Promise.reject(new Error(data.error?.message || '请求失败'))
  },
  (error) => {
    // HTTP 错误处理
    if (error.response) {
      const status = error.response.status

      switch (status) {
        case 401:
          ElMessage.error('未授权，请重新登录')
          // 跳转到登录页
          const authStore = useAuthStore()
          authStore.logout()
          break
        case 403:
          ElMessage.error('权限不足')
          break
        case 404:
          ElMessage.error('请求的资源不存在')
          break
        case 500:
          ElMessage.error('服务器错误')
          break
        default:
          ElMessage.error(error.message || '请求失败')
      }
    } else {
      ElMessage.error('网络错误，请检查连接')
    }

    return Promise.reject(error)
  }
)

export default service
```

**api/modules/project.ts - 项目管理 API:**

```typescript
import request from '@/utils/request'

export interface Project {
  id: string
  name: string
  description: string
  gitPlatform: 'github' | 'gitlab' | 'aws-codecommit'
  repoUrl: string
  enabled: boolean
  createdAt: string
  updatedAt: string
}

export interface ProjectCreateRequest {
  name: string
  description?: string
  gitPlatform: string
  repoUrl: string
  webhookSecret: string
}

export const projectApi = {
  // 获取项目列表
  getProjects: () => request.get<Project[]>('/api/v1/projects'),

  // 获取单个项目
  getProject: (id: string) => request.get<Project>(`/api/v1/projects/${id}`),

  // 创建项目
  createProject: (data: ProjectCreateRequest) =>
    request.post<Project>('/api/v1/projects', data),

  // 更新项目
  updateProject: (id: string, data: Partial<ProjectCreateRequest>) =>
    request.put<Project>(`/api/v1/projects/${id}`, data),

  // 删除项目
  deleteProject: (id: string) => request.delete(`/api/v1/projects/${id}`),
}
```

### Pinia 状态管理模式

**stores/modules/auth.ts - 认证状态:**

```typescript
import { defineStore } from 'pinia'
import { ref, computed } from 'vue'
import { authApi } from '@/api/modules/auth'

export const useAuthStore = defineStore('auth', () => {
  // State
  const token = ref<string | null>(localStorage.getItem('token'))
  const userInfo = ref<any>(null)

  // Getters
  const isAuthenticated = computed(() => !!token.value)

  // Actions
  async function login(username: string, password: string) {
    const data = await authApi.login({ username, password })
    token.value = data.token
    userInfo.value = data.user
    localStorage.setItem('token', data.token)
  }

  function logout() {
    token.value = null
    userInfo.value = null
    localStorage.removeItem('token')
    // 跳转到登录页
    window.location.href = '/login'
  }

  return {
    token,
    userInfo,
    isAuthenticated,
    login,
    logout,
  }
})
```

---

## 🔍 架构合规性

### 来源文档引用

- **架构文档**: `_bmad-output/planning-artifacts/architecture.md`
  - Frontend 技术栈规范（第 4 节）
  - Vue-Vben-Admin 初始化指南（第 2.2 节）
  - API 客户端模式（第 4.3 节）
  - State 管理模式（第 4.1 节）
  - 命名约定（第 3.2 节）

- **Epic 文档**: `_bmad-output/planning-artifacts/epics/epic-1.md`
  - Epic 1: 项目基础设施与配置管理
  - Story 1.2: 完整需求和验收标准

- **Web 研究来源**:
  - [Vue-Vben-Admin 官方文档](https://doc.vben.pro/en/guide/introduction/quick-start.html)
  - [Vue-Vben-Admin GitHub 仓库](https://github.com/vbenjs/vue-vben-admin)
  - [Vue-Vben-Admin 5.0 发布说明](https://github.com/vbenjs/vue-vben-admin/releases)

### 关键架构决策

1. **Vue-Vben-Admin 5.0** - 最新版本，提供企业级模板和 Monorepo 架构
2. **Composition API** - Vue 3 最佳实践，更好的类型推断和代码复用
3. **TypeScript Strict Mode** - 类型安全，减少运行时错误
4. **Pinia** - Vue 3 官方推荐的状态管理库（替代 Vuex）
5. **Axios 拦截器模式** - 统一的错误处理和 Token 管理
6. **环境变量配置** - 支持多环境部署（dev/prod）
7. **Vite 代理** - 开发环境 CORS 解决方案

---

## 🧪 测试要求

### 开发环境验证

- **启动验证**: `pnpm dev` 成功启动，无错误
- **页面加载**: 浏览器访问 http://localhost:5173 正常显示
- **HMR 验证**: 修改组件文件，浏览器自动刷新
- **控制台检查**: 无 JavaScript 错误或警告
- **API 代理**: 访问 /api/actuator/health 返回后端健康状态

### 生产构建验证

- **构建成功**: `pnpm build` 无错误完成
- **输出目录**: `dist/` 目录包含 index.html 和 assets/
- **代码分割**: 检查 assets/ 中存在多个 chunk 文件
- **预览验证**: `pnpm preview` 启动生产预览服务器

### 跨浏览器兼容性（可选）

- Chrome/Edge (Latest)
- Firefox (Latest)
- Safari (Latest)

---

## 📚 References (参考资源)

### 内部文档
- [Architecture Document - Frontend Section](../_bmad-output/planning-artifacts/architecture.md#frontend-architecture)
- [Epic 1 Requirements](../_bmad-output/planning-artifacts/epics/epic-1.md)
- [Story 1.1 - Backend Multi-Module Project](1-1-initialize-spring-boot-multi-module-project.md)

### 外部资源
- [Vue-Vben-Admin Official Documentation](https://doc.vben.pro/en/)
- [Vue-Vben-Admin Quick Start](https://doc.vben.pro/en/guide/introduction/quick-start.html)
- [Vue-Vben-Admin GitHub Repository](https://github.com/vbenjs/vue-vben-admin)
- [Vue 3 Official Documentation](https://vuejs.org/)
- [Vite Official Documentation](https://vitejs.dev/)
- [Pinia Official Documentation](https://pinia.vuejs.org/)
- [TypeScript Official Documentation](https://www.typescriptlang.org/)

---

## 🚀 Implementation Strategy (实现策略)

### 推荐方法：克隆官方模板（最快）

**步骤:**
1. 验证环境（Node.js 20.15.0+）
2. 安装 corepack: `npm i -g corepack`
3. 克隆 Vue-Vben-Admin 5.0
4. 安装依赖: `pnpm install`
5. 配置环境变量（`.env.development`）
6. 启动开发服务器: `pnpm dev`
7. 验证页面正常加载
8. 执行生产构建: `pnpm build`
9. 配置 CORS 代理

### 当前项目状态

**现有 frontend/ 目录:**
- 当前为空或不存在
- 准备初始化

**后端状态（来自 Story 1.1）:**
- Spring Boot 应用运行在 localhost:8080
- Actuator 端点: /actuator/health, /actuator/metrics
- 已配置 CORS（待验证）

**Git 状态:**
- 位于 master 分支
- 准备提交 Story 1.2 的更改

---

## 🎯 Definition of Done (完成定义)

- [ ] Vue-Vben-Admin 5.0 模板已克隆并初始化
- [ ] Node.js 20.15.0+ 和 pnpm (via corepack) 已安装
- [ ] 所有依赖已安装（`pnpm install` 成功）
- [ ] 目录结构符合验收标准（views/, components/, api/, stores/, 等）
- [ ] 配置文件已创建（vite.config.ts, tsconfig.json, .env.*）
- [ ] 开发服务器成功启动（`pnpm dev`）
- [ ] 浏览器可访问 http://localhost:5173 并正常显示
- [ ] 生产构建成功（`pnpm build`）
- [ ] dist/ 目录包含优化后的构建输出
- [ ] Vite 代理配置完成（开发环境可访问后端 API）
- [ ] 无控制台错误或构建警告
- [ ] 代码已提交到 Git

---

## 💡 Dev Agent Tips (开发 Agent 提示)

### 常见陷阱（必须避免）

❌ **不要做:**
- 使用 npm 或 yarn 安装依赖（**只能用 pnpm**）
- 克隆旧版本的 Vue-Vben-Admin（必须是 5.0）
- 跳过 corepack 配置（会导致 pnpm 版本不匹配）
- 在生产环境暴露 `.env.development` 文件
- 忘记配置 Vite 代理（会遇到 CORS 错误）
- 使用 Options API（应使用 Composition API）

✅ **必须做:**
- 验证 Node.js 版本 >= 20.15.0
- 使用 `npm i -g corepack` 安装 corepack
- 确认克隆的是 Vue-Vben-Admin **5.0 版本**
- 配置 `.env.development` 指向 `http://localhost:8080`
- 在 vite.config.ts 配置 API 代理
- 使用 TypeScript 和 Composition API
- 遵循项目的命名约定

### 常见问题排查

**问题 1: `pnpm install` 失败**
- 解决方案: 执行 `pnpm run reinstall`
- 原因: 依赖缓存或网络问题

**问题 2: 端口 5173 被占用**
- 解决方案: 修改 `vite.config.ts` 中的 `server.port`
- 或者终止占用端口的进程

**问题 3: API 请求 CORS 错误**
- 解决方案: 确认 vite.config.ts 配置了 proxy
- 确认后端已启动在 localhost:8080

**问题 4: TypeScript 类型错误**
- 解决方案: 确认 `tsconfig.json` 的 paths 与 vite alias 一致
- 重启 TypeScript 服务器（VS Code: Reload Window）

**问题 5: 构建体积过大**
- 解决方案: 检查 `manualChunks` 配置
- 移除未使用的依赖
- 启用 tree-shaking

### 效率提示

1. **使用 VS Code + Volar** - 最佳 Vue 3 开发体验
2. **安装 Vue DevTools** - 调试 Pinia Store 和组件
3. **启用 TypeScript IntelliSense** - 类型检查和自动补全
4. **使用 HMR** - 修改代码即时预览，无需刷新
5. **利用 Vben 内置组件** - 减少重复开发（表格、表单、弹窗等）

### 从 Story 1.1 学到的经验

**应用到 Story 1.2:**
- **严格遵循命名约定** - 组件 PascalCase, 函数 camelCase
- **多环境配置** - .env.development 和 .env.production
- **完整的验证步骤** - 开发启动 + 生产构建
- **详细的 Dev Notes** - 减少开发 Agent 犯错
- **清晰的依赖规则** - 只使用 pnpm，不使用其他包管理器

---

## 📝 Dev Agent Record (开发记录)

### Agent Model Used
_[将在实现时填写]_

### Implementation Plan
_[将在实现时填写]_

### Debug Log References
_[将在实现时填写]_

### Completion Notes List
_[将在实现时填写]_

### File List
_[将在实现时填写]_

---

**Story Created:** 2026-02-05
**Ready for Development:** ✅ YES
**Previous Story:** 1.1 - 从启动模板初始化 Spring Boot 多模块项目 (Done)
**Next Story:** 1.3 - 配置 PostgreSQL 数据库连接与 JPA (Backlog)
**Blocked By:** None
**Blocks:** Epic 8 的所有 Stories（前端界面开发依赖此基础）
