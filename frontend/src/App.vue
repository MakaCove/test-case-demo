<script setup lang="ts">
import { DataBoard, Document, Fold, Folder, Management, Expand, UserFilled, Files, Cpu, Tickets, Setting, EditPen, Download, Memo, List, Histogram } from '@element-plus/icons-vue'
import { computed, provide, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessageBox } from 'element-plus'
import { api } from './api/api'

const route = useRoute()
const router = useRouter()
const collapsed = ref(false)
const tableDensity = ref<'default' | 'small'>('default')
provide('tableDensity', tableDensity)

const showAside = computed(() => route.path !== '/login')
const asideWidth = computed(() => (collapsed.value ? '64px' : '220px'))
const breadcrumbs = computed(() =>
  route.matched
    .filter((item) => item.meta?.title)
    .map((item) => String(item.meta.title)),
)
const loginUserName = computed(() => {
  const raw = localStorage.getItem('userInfo')
  if (!raw) {
    return '未登录'
  }
  try {
    const parsed = JSON.parse(raw) as { displayName?: string; username?: string }
    return parsed.displayName || parsed.username || '用户'
  } catch {
    return '用户'
  }
})

async function onLogout() {
  const confirmed = await ElMessageBox.confirm('确认退出登录吗？', '提示', {
    type: 'warning',
  }).catch(() => false)
  if (!confirmed) {
    return
  }
  try {
    await api.logout()
  } finally {
    localStorage.removeItem('token')
    localStorage.removeItem('userInfo')
    await router.replace('/login')
  }
}
</script>

<template>
  <el-container v-if="showAside" class="app-layout">
    <el-aside :width="asideWidth" class="app-aside" :class="{ collapsed }">
      <div class="brand">
        <h1 v-if="!collapsed">AI测试平台</h1>
        <p v-if="!collapsed">Test Case Studio</p>
        <h1 v-else class="brand-icon">AI</h1>
      </div>
      <div class="side-menu-scroll-host">
        <el-menu router :default-active="$route.path" class="side-menu" :collapse="collapsed">
          <el-menu-item index="/dashboard">
            <el-icon><DataBoard /></el-icon>
            <template #title>看板</template>
          </el-menu-item>
          <el-menu-item index="/projects">
            <el-icon><Folder /></el-icon>
            <template #title>项目管理</template>
          </el-menu-item>
          <el-menu-item index="/versions">
            <el-icon><Management /></el-icon>
            <template #title>版本管理</template>
          </el-menu-item>
          <el-menu-item index="/assets">
            <el-icon><Files /></el-icon>
            <template #title>用例需求库</template>
          </el-menu-item>
          <el-menu-item index="/ui-nl-cases">
            <el-icon><Memo /></el-icon>
            <template #title>UI自然语言用例库</template>
          </el-menu-item>
          <el-menu-item index="/generation-tasks">
            <el-icon><Cpu /></el-icon>
            <template #title>用例任务中心</template>
          </el-menu-item>
          <el-menu-item index="/ui-nl-tasks">
            <el-icon><Cpu /></el-icon>
            <template #title>UI自然语言任务中心</template>
          </el-menu-item>
          <el-menu-item index="/test-cases">
            <el-icon><Tickets /></el-icon>
            <template #title>功能测试用例</template>
          </el-menu-item>
          <el-menu-item index="/api-test-cases">
            <el-icon><Tickets /></el-icon>
            <template #title>接口测试用例</template>
          </el-menu-item>
          <el-menu-item index="/ui-nl-steps">
            <el-icon><List /></el-icon>
            <template #title>UI步骤管理</template>
          </el-menu-item>
          <el-menu-item index="/ui-nl-reports">
            <el-icon><Histogram /></el-icon>
            <template #title>UI测试报告</template>
          </el-menu-item>
          <el-menu-item index="/model-configs">
            <el-icon><Setting /></el-icon>
            <template #title>模型配置</template>
          </el-menu-item>
          <el-menu-item index="/prompt-templates">
            <el-icon><EditPen /></el-icon>
            <template #title>Prompt模板</template>
          </el-menu-item>
          <el-menu-item index="/exports">
            <el-icon><Download /></el-icon>
            <template #title>导出中心</template>
          </el-menu-item>
          <el-menu-item index="/operation-logs">
            <el-icon><Document /></el-icon>
            <template #title>操作日志</template>
          </el-menu-item>
        </el-menu>
      </div>
      <div class="aside-bottom">
        <div class="aside-user-row" :class="{ 'is-collapsed': collapsed }">
          <el-avatar :size="32" :icon="UserFilled" />
          <span v-if="!collapsed" class="user-name" :title="loginUserName">{{ loginUserName }}</span>
        </div>
        <el-button
          v-if="!collapsed"
          class="logout-row"
          type="danger"
          plain
          size="small"
          @click="onLogout"
        >
          退出登录
        </el-button>
        <el-button
          v-else
          class="logout-row logout-row--collapsed"
          type="danger"
          plain
          size="small"
          @click="onLogout"
        >
          退出
        </el-button>
      </div>
    </el-aside>

    <el-container>
      <el-header class="app-header">
        <div class="header-left">
          <el-button class="collapse-btn" text @click="collapsed = !collapsed">
            <el-icon :size="18">
              <Expand v-if="collapsed" />
              <Fold v-else />
            </el-icon>
          </el-button>
          <el-divider direction="vertical" />
          <el-breadcrumb separator="/">
            <el-breadcrumb-item>首页</el-breadcrumb-item>
            <el-breadcrumb-item v-for="item in breadcrumbs" :key="item">{{ item }}</el-breadcrumb-item>
          </el-breadcrumb>
        </div>
        <div class="header-right">
          <el-radio-group v-model="tableDensity" size="small">
            <el-radio-button value="default">舒适</el-radio-button>
            <el-radio-button value="small">紧凑</el-radio-button>
          </el-radio-group>
        </div>
      </el-header>
      <el-main class="app-main">
        <div class="content-panel">
          <router-view />
        </div>
      </el-main>
    </el-container>
  </el-container>

  <div v-else class="auth-layout">
    <router-view />
  </div>
</template>

<style scoped>
.app-layout {
  height: 100vh;
  min-height: 0;
  background: #f3f6fb;
  overflow: hidden;
}

.app-aside.el-aside {
  border-right: 1px solid #e5eaf3;
  background: #ffffff;
  transition: width 0.25s ease;
  display: flex;
  flex-direction: column;
  /* EP 默认 .el-aside { overflow: auto }，与内部滚动叠加易触发 Chromium 下 SVG 绘制异常 */
  overflow: hidden;
}

.brand {
  padding: 18px 16px 10px;
  white-space: nowrap;
}

.brand h1 {
  margin: 0;
  font-size: 17px;
  font-weight: 700;
}

.brand-icon {
  text-align: center;
}

.brand p {
  margin: 6px 0 0;
  color: #909399;
  font-size: 12px;
}

/* 侧栏菜单：滚动在宿主上，菜单不设 overflow:hidden；条目单独开合成层，缓解 Chromium 滚动后 SVG 图标空白 */
.side-menu-scroll-host {
  flex: 1;
  min-height: 0;
  min-width: 0;
  overflow-x: hidden;
  overflow-y: auto;
  overscroll-behavior: contain;
  scrollbar-color: rgba(17, 24, 39, 0.28) transparent;
  scrollbar-width: thin;
}

.side-menu-scroll-host::-webkit-scrollbar {
  width: 8px;
}

.side-menu-scroll-host::-webkit-scrollbar-thumb {
  border-radius: 8px;
  background-color: rgba(17, 24, 39, 0.28);
}

.side-menu {
  border-right: 0;
  overflow: visible;
}

.side-menu:not(.el-menu--collapse) {
  width: 220px;
}

.side-menu :deep(.el-menu-item) {
  transform: translateZ(0);
  backface-visibility: hidden;
}

.side-menu :deep(.el-menu-item .el-icon) {
  flex-shrink: 0;
  font-size: 18px;
  vertical-align: middle;
}

.side-menu :deep(.el-menu-item .el-icon svg) {
  display: block;
  vertical-align: unset;
}

.app-header {
  height: 56px;
  padding: 0 18px;
  background: #ffffff;
  border-bottom: 1px solid #e5eaf3;
  display: flex;
  align-items: center;
  justify-content: space-between;
}

.app-main {
  min-height: 0;
  overflow: auto;
}

.header-left {
  display: flex;
  gap: 8px;
  align-items: center;
}

.header-right {
  display: flex;
  gap: 10px;
  align-items: center;
}

.collapse-btn {
  padding: 4px 8px;
}

.app-main {
  padding: 14px 16px;
  background: transparent;
}

.content-panel {
  background: #fff;
  border-radius: 10px;
  padding: 14px 16px 16px;
  box-shadow: 0 4px 16px rgba(15, 23, 42, 0.04);
  height: calc(100vh - 84px);
  overflow: auto;
}

.auth-layout {
  min-height: 100vh;
}

.aside-bottom {
  border-top: 1px solid #eef2f7;
  padding: 10px 12px;
  display: flex;
  flex-direction: column;
  align-items: stretch;
  gap: 8px;
}

.aside-user-row {
  display: flex;
  align-items: center;
  gap: 10px;
  min-width: 0;
}

.aside-user-row.is-collapsed {
  justify-content: center;
}

.user-name {
  flex: 1;
  min-width: 0;
  font-size: 13px;
  color: #606266;
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
}

.logout-row {
  width: 100%;
  margin: 0;
  padding: 8px 12px;
  height: auto;
  min-height: 34px;
  justify-content: center;
  box-sizing: border-box;
}

.logout-row--collapsed {
  align-self: stretch;
  padding: 8px 4px;
}

.app-aside.collapsed .aside-bottom {
  padding: 10px 6px;
}

.app-aside.collapsed .brand {
  padding: 18px 8px 10px;
}

@media (min-width: 1900px) {
  .app-header {
    height: 60px;
    padding: 0 24px;
  }

  .app-main {
    padding: 16px 20px;
  }

  .content-panel {
    height: calc(100vh - 92px);
    padding: 16px 20px 20px;
  }
}
</style>
