<script setup lang="ts">
import {
  House, OfficeBuilding, Collection, FolderOpened, DocumentCopy, MagicStick, Monitor,
  Tickets, Connection, List, DataAnalysis, Setting, Reading, Download, Operation,
  Fold, Expand, UserFilled,
  SwitchButton, ArrowDown, Key,
} from '@element-plus/icons-vue'
import { computed, onBeforeUnmount, provide, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessageBox, ElDropdown, ElDropdownMenu, ElDropdownItem } from 'element-plus'
import { api } from './api/api'
import defaultAvatar from './assets/default-avatar.svg'

const route = useRoute()
const router = useRouter()
const collapsed = ref(false)
const sideMenuIsScrolling = ref(false)
let sideMenuScrollTimer: ReturnType<typeof setTimeout> | null = null
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
  if (!raw) return '未登录'
  try {
    const parsed = JSON.parse(raw) as { displayName?: string; username?: string }
    return parsed.displayName || parsed.username || '用户'
  } catch {
    return '用户'
  }
})
const changePasswordVisible = ref(false)
const changingPassword = ref(false)
const changePasswordForm = ref({
  oldPassword: '',
  newPassword: '',
  confirmPassword: '',
})

async function onLogout() {
  const confirmed = await ElMessageBox.confirm('确认退出登录吗？', '提示', {
    type: 'warning',
  }).catch(() => false)
  if (!confirmed) return
  try {
    await api.logout()
  } finally {
    localStorage.removeItem('token')
    localStorage.removeItem('userInfo')
    await router.replace('/login')
  }
}

function openChangePassword() {
  changePasswordForm.value = {
    oldPassword: '',
    newPassword: '',
    confirmPassword: '',
  }
  changePasswordVisible.value = true
}

async function submitChangePassword() {
  const { oldPassword, newPassword, confirmPassword } = changePasswordForm.value
  if (!oldPassword || !newPassword || !confirmPassword) {
    await ElMessageBox.alert('请补全密码信息', '提示')
    return
  }
  if (newPassword.length < 6 || newPassword.length > 64) {
    await ElMessageBox.alert('新密码长度需为 6-64 位', '提示')
    return
  }
  if (newPassword !== confirmPassword) {
    await ElMessageBox.alert('两次输入的新密码不一致', '提示')
    return
  }
  changingPassword.value = true
  try {
    await api.changePassword({
      oldPassword,
      newPassword,
    })
    changePasswordVisible.value = false
    await ElMessageBox.alert('密码已修改，请重新登录', '提示')
    localStorage.removeItem('token')
    localStorage.removeItem('userInfo')
    await router.replace('/login')
  } finally {
    changingPassword.value = false
  }
}

function goDashboard() {
  if (route.path !== '/dashboard') {
    router.push('/dashboard')
  }
}

function onSideMenuScroll() {
  sideMenuIsScrolling.value = true
  if (sideMenuScrollTimer) clearTimeout(sideMenuScrollTimer)
  sideMenuScrollTimer = setTimeout(() => {
    sideMenuIsScrolling.value = false
    sideMenuScrollTimer = null
  }, 700)
}

onBeforeUnmount(() => {
  if (sideMenuScrollTimer) {
    clearTimeout(sideMenuScrollTimer)
    sideMenuScrollTimer = null
  }
})
</script>

<template>
  <el-container v-if="showAside" class="app-layout">
    <el-aside :width="asideWidth" class="app-aside" :class="{ collapsed }">
      <!-- 品牌区 -->
      <div class="brand" role="button" tabindex="0" @click="goDashboard" @keydown.enter="goDashboard">
        <div v-if="!collapsed" class="brand-full">
          <div class="brand-logo">AI</div>
          <div class="brand-text">
            <span class="brand-name">AI 测试平台</span>
            <span class="brand-sub">Test Case Studio</span>
          </div>
        </div>
        <div v-else class="brand-icon-only">AI</div>
      </div>

      <!-- 菜单区 -->
      <div
        class="side-menu-scroll-host"
        :class="{ 'is-scrolling': sideMenuIsScrolling }"
        @scroll.passive="onSideMenuScroll"
      >
        <el-menu router :default-active="$route.path" class="side-menu" :collapse="collapsed">
          <!-- 概览 -->
          <el-menu-item index="/dashboard">
            <el-icon><House /></el-icon>
            <template #title>工作台</template>
          </el-menu-item>

          <!-- 分组：资产管理 -->
          <div v-if="!collapsed" class="menu-group-label">资产管理</div>
          <div v-else class="menu-group-divider" />
          <el-menu-item index="/projects">
            <el-icon><OfficeBuilding /></el-icon>
            <template #title>项目管理</template>
          </el-menu-item>
          <el-menu-item index="/versions">
            <el-icon><Collection /></el-icon>
            <template #title>版本管理</template>
          </el-menu-item>
          <el-menu-item index="/assets">
            <el-icon><FolderOpened /></el-icon>
            <template #title>需求资产库</template>
          </el-menu-item>
          <el-menu-item index="/ui-nl-cases">
            <el-icon><DocumentCopy /></el-icon>
            <template #title>UI 用例库</template>
          </el-menu-item>

          <!-- 分组：任务中心 -->
          <div v-if="!collapsed" class="menu-group-label">任务中心</div>
          <div v-else class="menu-group-divider" />
          <el-menu-item index="/generation-tasks">
            <el-icon><MagicStick /></el-icon>
            <template #title>用例生成任务</template>
          </el-menu-item>
          <el-menu-item index="/ui-nl-tasks">
            <el-icon><Monitor /></el-icon>
            <template #title>UI 自然语言任务</template>
          </el-menu-item>

          <!-- 分组：测试用例 -->
          <div v-if="!collapsed" class="menu-group-label">测试用例</div>
          <div v-else class="menu-group-divider" />
          <el-menu-item index="/test-cases">
            <el-icon><Tickets /></el-icon>
            <template #title>功能测试用例</template>
          </el-menu-item>
          <el-menu-item index="/api-test-cases">
            <el-icon><Connection /></el-icon>
            <template #title>接口测试用例</template>
          </el-menu-item>
          <el-menu-item index="/ui-nl-steps">
            <el-icon><List /></el-icon>
            <template #title>UI 步骤管理</template>
          </el-menu-item>
          <el-menu-item index="/ui-nl-reports">
            <el-icon><DataAnalysis /></el-icon>
            <template #title>UI 测试报告</template>
          </el-menu-item>

          <!-- 分组：系统配置 -->
          <div v-if="!collapsed" class="menu-group-label">系统</div>
          <div v-else class="menu-group-divider" />
          <el-menu-item index="/model-configs">
            <el-icon><Setting /></el-icon>
            <template #title>模型配置</template>
          </el-menu-item>
          <el-menu-item index="/prompt-templates">
            <el-icon><Reading /></el-icon>
            <template #title>Prompt 模板</template>
          </el-menu-item>
          <el-menu-item index="/exports">
            <el-icon><Download /></el-icon>
            <template #title>导出中心</template>
          </el-menu-item>
          <el-menu-item index="/operation-logs">
            <el-icon><Operation /></el-icon>
            <template #title>操作日志</template>
          </el-menu-item>
        </el-menu>
      </div>

      <!-- 用户区 -->
      <div class="aside-bottom">
        <el-dropdown trigger="click" placement="top-start">
          <div class="aside-user-row" :class="{ 'is-collapsed': collapsed }">
            <el-avatar :size="32" :src="defaultAvatar" :icon="UserFilled" class="user-avatar" />
            <template v-if="!collapsed">
              <span class="user-name" :title="loginUserName">{{ loginUserName }}</span>
              <el-icon class="user-arrow"><ArrowDown /></el-icon>
            </template>
          </div>
          <template #dropdown>
            <el-dropdown-menu>
              <el-dropdown-item disabled>
                <span class="dropdown-username">{{ loginUserName }}</span>
              </el-dropdown-item>
              <el-dropdown-item :icon="Key" @click="openChangePassword">
                修改密码
              </el-dropdown-item>
              <el-dropdown-item divided :icon="SwitchButton" @click="onLogout">
                退出登录
              </el-dropdown-item>
            </el-dropdown-menu>
          </template>
        </el-dropdown>
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

  <el-dialog
    v-model="changePasswordVisible"
    title="修改密码"
    width="460px"
    :close-on-click-modal="false"
  >
    <el-form label-position="top">
      <el-form-item label="原密码">
        <el-input v-model="changePasswordForm.oldPassword" type="password" show-password autocomplete="current-password" />
      </el-form-item>
      <el-form-item label="新密码">
        <el-input v-model="changePasswordForm.newPassword" type="password" show-password autocomplete="new-password" />
      </el-form-item>
      <el-form-item label="确认新密码">
        <el-input v-model="changePasswordForm.confirmPassword" type="password" show-password autocomplete="new-password" @keydown.enter="submitChangePassword" />
      </el-form-item>
    </el-form>
    <template #footer>
      <el-button @click="changePasswordVisible = false">取消</el-button>
      <el-button type="primary" :loading="changingPassword" @click="submitChangePassword">确认修改</el-button>
    </template>
  </el-dialog>
</template>

<style scoped>
/* ── 整体布局 ── */
.app-layout {
  height: 100vh;
  min-height: 0;
  background: transparent;
  overflow: hidden;
}

/* ── 侧边栏 ── */
.app-aside.el-aside {
  border-right: 1px solid var(--app-card-border);
  background: var(--app-surface);
  backdrop-filter: blur(8px);
  transition: width 0.25s ease;
  display: flex;
  flex-direction: column;
  overflow: hidden;
}

/* 品牌区 */
.brand {
  padding: 18px 14px 14px;
  white-space: nowrap;
  flex-shrink: 0;
  cursor: pointer;
  border-radius: 10px;
  transition: background-color 0.18s ease;
}

.brand:hover {
  background: #f5f8fd;
}

.brand-full {
  display: flex;
  align-items: center;
  gap: 10px;
}

.brand-logo {
  width: 34px;
  height: 34px;
  border-radius: 8px;
  background: linear-gradient(135deg, #409eff 0%, #6366f1 100%);
  color: #fff;
  font-size: 13px;
  font-weight: 800;
  display: flex;
  align-items: center;
  justify-content: center;
  flex-shrink: 0;
  letter-spacing: -0.5px;
}

.brand-text {
  display: flex;
  flex-direction: column;
  gap: 1px;
  min-width: 0;
}

.brand-name {
  font-size: 14px;
  font-weight: 700;
  color: var(--app-text-primary);
  line-height: 1.2;
}

.brand-sub {
  font-size: 11px;
  color: var(--app-text-muted);
  line-height: 1.2;
}

.brand-icon-only {
  width: 36px;
  height: 36px;
  border-radius: 8px;
  background: linear-gradient(135deg, #409eff 0%, #6366f1 100%);
  color: #fff;
  font-size: 12px;
  font-weight: 800;
  display: flex;
  align-items: center;
  justify-content: center;
  margin: 0 auto;
  letter-spacing: -0.5px;
}

/* 菜单分组标签 */
.menu-group-label {
  padding: 14px 16px 6px;
  font-size: 11px;
  font-weight: 600;
  color: #9aa9ba;
  text-transform: uppercase;
  letter-spacing: 0.8px;
  user-select: none;
}

.menu-group-divider {
  margin: 8px 14px;
  border-top: 1px solid var(--app-card-border);
}

/* 菜单滚动容器 */
.side-menu-scroll-host {
  flex: 1;
  min-height: 0;
  min-width: 0;
  overflow-x: hidden;
  overflow-y: auto;
  padding: 4px 0 10px;
  overscroll-behavior: contain;
  scrollbar-color: transparent transparent;
  scrollbar-width: none;
}

.side-menu-scroll-host::-webkit-scrollbar { width: 0; }
.side-menu-scroll-host::-webkit-scrollbar-thumb {
  border-radius: 8px;
  background-color: transparent;
}

.side-menu-scroll-host:hover,
.side-menu-scroll-host.is-scrolling {
  scrollbar-color: rgba(17, 24, 39, 0.2) transparent;
  scrollbar-width: thin;
}
.side-menu-scroll-host:hover::-webkit-scrollbar,
.side-menu-scroll-host.is-scrolling::-webkit-scrollbar { width: 4px; }
.side-menu-scroll-host:hover::-webkit-scrollbar-thumb,
.side-menu-scroll-host.is-scrolling::-webkit-scrollbar-thumb {
  background-color: rgba(17, 24, 39, 0.2);
}

.side-menu {
  border-right: 0;
  padding: 0 6px 8px;
  background: transparent;
  overflow: visible;
}

.side-menu:not(.el-menu--collapse) {
  width: 220px;
}

.side-menu :deep(.el-menu-item) {
  position: relative;
  transform: translateZ(0);
  backface-visibility: hidden;
  height: 38px;
  line-height: 38px;
  font-size: 13px;
  border-radius: 10px;
  margin: 2px 8px;
  width: calc(100% - 16px);
  color: #56667b;
  overflow: hidden;
  transition: background-color 0.18s ease, color 0.18s ease, transform 0.18s ease, box-shadow 0.18s ease;
}

.side-menu :deep(.el-menu-item)::before {
  content: '';
  position: absolute;
  left: 7px;
  top: 50%;
  width: 3px;
  height: 18px;
  border-radius: 2px;
  margin-top: -9px;
  background: transparent;
  transition: background-color 0.18s ease;
}

.side-menu :deep(.el-menu-item:hover) {
  background: #f4f7fc;
  color: var(--app-text-primary);
  transform: translateX(2px);
}

.side-menu :deep(.el-menu-item:hover)::before {
  background: #c4d4ea;
}

.side-menu :deep(.el-menu-item.is-active) {
  background: linear-gradient(90deg, #eaf2ff 0%, #f4f8ff 100%);
  color: #2f76e4;
  font-weight: 600;
  box-shadow: inset 0 0 0 1px #c7dbfb, 0 4px 10px rgba(64, 116, 188, 0.12);
}

.side-menu :deep(.el-menu-item.is-active)::before {
  background: #3b82f6;
}

.side-menu :deep(.el-menu-item .el-icon) {
  width: 20px;
  height: 20px;
  display: inline-flex;
  align-items: center;
  justify-content: center;
  flex-shrink: 0;
  font-size: 16px;
  color: #66788f;
  vertical-align: middle;
  margin-right: 10px;
  border-radius: 6px;
  transition: color 0.18s ease, background-color 0.18s ease, transform 0.18s ease;
}

.side-menu :deep(.el-menu-item:hover .el-icon) {
  color: #2f76e4;
  background: rgba(90, 137, 207, 0.12);
  transform: translateY(-0.5px);
}

.side-menu :deep(.el-menu-item.is-active .el-icon) {
  color: #2f76e4;
  background: rgba(90, 137, 207, 0.14);
}

.side-menu :deep(.el-menu-item .el-icon svg) {
  display: block;
  width: 16px;
  height: 16px;
  vertical-align: unset;
}

/* 折叠状态下菜单项居中 */
.app-aside.collapsed .side-menu :deep(.el-menu-item) {
  margin: 1px 4px;
  width: calc(100% - 8px);
  justify-content: center;
  transform: none !important;
}

.app-aside.collapsed .side-menu {
  padding: 0 4px 8px;
}

.app-aside.collapsed .side-menu :deep(.el-menu-item)::before {
  left: 4px;
  width: 2px;
  height: 14px;
  margin-top: -7px;
}

.app-aside.collapsed .side-menu :deep(.el-menu-item .el-icon) {
  margin-right: 0;
}

/* 用户区 */
.aside-bottom {
  border-top: 1px solid var(--app-card-border);
  padding: 12px;
  display: flex;
  flex-direction: column;
  gap: 6px;
  flex-shrink: 0;
}

.aside-user-row {
  display: flex;
  align-items: center;
  gap: 10px;
  min-width: 0;
  padding: 6px 8px;
  border-radius: 8px;
  cursor: pointer;
  transition: background 0.15s;
}

.aside-user-row:hover {
  background: rgba(220, 233, 249, 0.46);
}

.aside-user-row.is-collapsed {
  justify-content: center;
  padding: 6px 4px;
}

.user-avatar {
  flex-shrink: 0;
  background: var(--el-color-primary-light-7);
  color: var(--el-color-primary);
}

.user-name {
  flex: 1;
  min-width: 0;
  font-size: 13px;
  font-weight: 500;
  color: var(--app-text-primary);
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
}

.user-arrow {
  flex-shrink: 0;
  color: #9eabbb;
  font-size: 12px;
}

.dropdown-username {
  font-weight: 600;
  color: var(--app-text-primary);
}

.app-aside.collapsed .aside-bottom {
  padding: 10px 4px;
}

.app-aside.collapsed .aside-bottom :deep(.el-dropdown) {
  display: block;
  width: 100%;
}

.app-aside.collapsed .aside-user-row {
  width: calc(100% - 8px);
  margin: 1px 4px;
  border-radius: 10px;
  justify-content: center;
  padding: 6px 0;
}

/* ── 顶部 Header ── */
.app-header {
  height: 56px;
  padding: 0 20px;
  background: rgba(246, 251, 255, 0.62);
  border-bottom: 1px solid var(--app-card-border);
  backdrop-filter: blur(8px);
  display: flex;
  align-items: center;
  justify-content: space-between;
  flex-shrink: 0;
}

.header-left {
  display: flex;
  gap: 10px;
  align-items: center;
}

.header-right {
  display: flex;
  gap: 12px;
  align-items: center;
}

.collapse-btn {
  padding: 4px 8px;
}

/* ── 内容区 ── */
.app-main {
  min-height: 0;
  overflow: auto;
  padding: var(--space-4);
  background: transparent;
}

.content-panel {
  background: var(--app-surface);
  border: 1px solid var(--app-card-border);
  border-radius: var(--app-radius-lg);
  padding: var(--space-4);
  box-shadow: var(--app-shadow-md);
  backdrop-filter: blur(10px);
  height: calc(100vh - 88px);
  overflow: auto;
}

/* ── 登录布局 ── */
.auth-layout {
  min-height: 100vh;
  padding: var(--space-3);
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
