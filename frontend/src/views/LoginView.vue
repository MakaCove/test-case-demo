<script setup lang="ts">
import { computed, ref } from 'vue'
import { api } from '../api/api'
import { ElMessage } from 'element-plus'
import { useRoute, useRouter } from 'vue-router'
import { Check, Lock, User } from '@element-plus/icons-vue'

const authMode = ref<'login' | 'register'>('login')
const username = ref('')
const password = ref('')
const registerUsername = ref('')
const registerPassword = ref('')
const registerConfirmPassword = ref('')
const loading = ref(false)
const route = useRoute()
const router = useRouter()
const isRegisterMode = computed(() => authMode.value === 'register')

const capabilities = [
  '需求资产、任务、测试用例统一沉淀',
  'AI 辅助生成与编排，减少重复工作',
  '全流程可追踪，质量与效率双提升',
]

async function onSubmit() {
  if (isRegisterMode.value) {
    await onRegister()
    return
  }
  if (!username.value.trim() || !password.value) {
    ElMessage.warning('请填写用户名和密码')
    return
  }
  loading.value = true
  try {
    const result = await api.login(username.value.trim(), password.value)
    localStorage.setItem('token', result.token)
    localStorage.setItem('userInfo', JSON.stringify(result.userInfo))
    ElMessage.success(`欢迎回来，${result.userInfo.displayName}`)
    const redirect = typeof route.query.redirect === 'string' && route.query.redirect.startsWith('/')
      ? route.query.redirect
      : '/dashboard'
    await router.replace(redirect)
  } catch (error) {
    ElMessage.error((error as Error).message)
  } finally {
    loading.value = false
  }
}

async function onRegister() {
  const u = registerUsername.value.trim()
  if (!u || !registerPassword.value || !registerConfirmPassword.value) {
    ElMessage.warning('请补全注册信息')
    return
  }
  if (u.length < 3 || u.length > 32) {
    ElMessage.warning('用户名长度需为 3-32 位')
    return
  }
  if (registerPassword.value.length < 6 || registerPassword.value.length > 64) {
    ElMessage.warning('密码长度需为 6-64 位')
    return
  }
  if (registerPassword.value !== registerConfirmPassword.value) {
    ElMessage.warning('两次输入的密码不一致')
    return
  }
  loading.value = true
  try {
    const result = await api.register({
      username: u,
      password: registerPassword.value,
    })
    localStorage.setItem('token', result.token)
    localStorage.setItem('userInfo', JSON.stringify(result.userInfo))
    ElMessage.success(`注册成功，欢迎你 ${result.userInfo.displayName}`)
    await router.replace('/dashboard')
  } catch (error) {
    ElMessage.error((error as Error).message)
  } finally {
    loading.value = false
  }
}

function switchMode(mode: 'login' | 'register') {
  authMode.value = mode
  loading.value = false
}
</script>

<template>
  <div class="login-page">
    <div class="mesh mesh--left" />
    <div class="mesh mesh--right" />

    <div class="login-layout">
      <section class="visual-panel">
        <div class="brand-row">
          <div class="brand-logo">AI</div>
          <div class="brand-meta">
            <p class="brand-cn">AI 测试平台</p>
            <p class="brand-en">Test Case Studio</p>
          </div>
        </div>

        <h1 class="headline">让测试设计与执行，回到高效协同</h1>
        <p class="headline-sub">
          从需求理解到用例交付，统一在一个平台完成，
          进度实时可视，交付质量持续可控。
        </p>

        <div class="data-cards">
          <article class="data-card">
            <div class="data-label">资产沉淀</div>
            <div class="data-value">Reusable</div>
            <div class="data-desc">需求结构化沉淀，经验可复用可传承</div>
          </article>
          <article class="data-card">
            <div class="data-label">任务执行</div>
            <div class="data-value">Traceable</div>
            <div class="data-desc">执行进展与风险状态全程透明可追踪</div>
          </article>
        </div>
      </section>

      <section class="auth-panel">
        <div class="auth-card">
          <div class="auth-head">
            <h2>{{ isRegisterMode ? '创建账号' : '欢迎登录' }}</h2>
            <p>{{ isRegisterMode ? '注册后可直接进入平台开始使用' : '输入账号信息，开始今天的测试工作' }}</p>
            <div class="auth-mode-switch">
              <button
                type="button"
                class="auth-mode-btn"
                :class="{ 'is-active': !isRegisterMode }"
                @click="switchMode('login')"
              >
                登录
              </button>
              <button
                type="button"
                class="auth-mode-btn"
                :class="{ 'is-active': isRegisterMode }"
                @click="switchMode('register')"
              >
                注册
              </button>
            </div>
          </div>

          <el-form class="login-form" label-position="top" @submit.prevent="onSubmit">
            <el-form-item v-if="!isRegisterMode" label="用户名">
              <el-input
                v-model="username"
                size="large"
                placeholder="请输入用户名"
                autocomplete="username"
                clearable
              >
                <template #prefix>
                  <el-icon><User /></el-icon>
                </template>
              </el-input>
            </el-form-item>

            <el-form-item v-else label="登录用户名">
              <el-input
                v-model="registerUsername"
                size="large"
                placeholder="请输入用户名（3-32位）"
                autocomplete="username"
                clearable
              >
                <template #prefix>
                  <el-icon><User /></el-icon>
                </template>
              </el-input>
            </el-form-item>

            <el-form-item v-if="!isRegisterMode" label="密码">
              <el-input
                v-model="password"
                type="password"
                size="large"
                show-password
                placeholder="请输入密码"
                autocomplete="current-password"
                @keydown.enter="onSubmit"
              >
                <template #prefix>
                  <el-icon><Lock /></el-icon>
                </template>
              </el-input>
            </el-form-item>

            <el-form-item v-else label="登录密码">
              <el-input
                v-model="registerPassword"
                type="password"
                size="large"
                show-password
                placeholder="请输入密码（6-64位）"
                autocomplete="new-password"
                @keydown.enter="onSubmit"
              >
                <template #prefix>
                  <el-icon><Lock /></el-icon>
                </template>
              </el-input>
            </el-form-item>

            <el-form-item v-if="isRegisterMode" label="确认密码">
              <el-input
                v-model="registerConfirmPassword"
                type="password"
                size="large"
                show-password
                placeholder="请再次输入密码"
                autocomplete="new-password"
                @keydown.enter="onSubmit"
              >
                <template #prefix>
                  <el-icon><Lock /></el-icon>
                </template>
              </el-input>
            </el-form-item>

            <el-button
              class="login-btn"
              type="primary"
              size="large"
              :loading="loading"
              @click="onSubmit"
            >
              {{ loading ? (isRegisterMode ? '注册中...' : '登录中...') : (isRegisterMode ? '注册并进入平台' : '进入工作台') }}
            </el-button>
          </el-form>

          <ul class="capability-list">
            <li v-for="item in capabilities" :key="item">
              <el-icon><Check /></el-icon>
              <span>{{ item }}</span>
            </li>
          </ul>
        </div>
      </section>
    </div>
  </div>
</template>

<style scoped>
.login-page {
  position: relative;
  min-height: 100vh;
  overflow: hidden;
  background: transparent;
  display: flex;
  align-items: center;
  justify-content: center;
  padding: 32px 24px;
}

.mesh {
  position: absolute;
  width: 42vw;
  height: 42vw;
  max-width: 580px;
  max-height: 580px;
  border-radius: 50%;
  pointer-events: none;
  filter: blur(12px);
}

.mesh--left {
  top: -180px;
  left: -160px;
  background: radial-gradient(circle, rgba(64, 158, 255, 0.3) 0%, rgba(64, 158, 255, 0) 70%);
}

.mesh--right {
  right: -180px;
  bottom: -210px;
  background: radial-gradient(circle, rgba(99, 102, 241, 0.22) 0%, rgba(99, 102, 241, 0) 72%);
}

.login-layout {
  position: relative;
  z-index: 1;
  width: min(1120px, 100%);
  border-radius: var(--app-radius-lg);
  border: 1px solid var(--app-card-border);
  background: var(--app-surface);
  backdrop-filter: blur(10px);
  box-shadow: var(--app-shadow-md);
  display: grid;
  grid-template-columns: 1.05fr 0.95fr;
}

.visual-panel {
  padding: 52px 54px;
  border-right: 1px solid var(--app-card-border);
  display: flex;
  flex-direction: column;
}

.brand-row {
  display: flex;
  align-items: center;
  gap: 12px;
}

.brand-logo {
  width: 40px;
  height: 40px;
  border-radius: 12px;
  background: linear-gradient(135deg, #409eff 0%, #6366f1 100%);
  color: #fff;
  font-size: 14px;
  font-weight: 800;
  letter-spacing: -0.3px;
  display: flex;
  align-items: center;
  justify-content: center;
}

.brand-meta p {
  margin: 0;
}

.brand-cn {
  font-size: 17px;
  font-weight: 700;
  color: var(--app-text-primary);
}

.brand-en {
  margin-top: 3px;
  font-size: 12px;
  color: var(--app-text-muted);
}

.headline {
  margin: 40px 0 0;
  font-size: 34px;
  line-height: 1.28;
  color: var(--app-text-primary);
  letter-spacing: 0.3px;
}

.headline-sub {
  margin: 18px 0 0;
  max-width: 520px;
  font-size: 14px;
  line-height: 1.78;
  color: var(--app-text-secondary);
}

.data-cards {
  margin-top: auto;
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: 14px;
}

.data-card {
  border: 1px solid var(--app-card-border);
  border-radius: var(--app-radius-sm);
  background: rgba(255, 255, 255, 0.58);
  padding: 18px;
  transition: transform 0.18s ease, box-shadow 0.18s ease, border-color 0.18s ease;
}

.data-card:hover {
  transform: translateY(-2px);
  border-color: var(--app-card-border-strong);
  box-shadow: var(--app-shadow-sm);
}

.data-label {
  font-size: 12px;
  color: var(--app-text-muted);
}

.data-value {
  margin-top: 5px;
  font-size: 22px;
  font-weight: 700;
  color: #2656d8;
}

.data-desc {
  margin-top: 7px;
  font-size: 12px;
  line-height: 1.6;
  color: var(--app-text-secondary);
}

.auth-panel {
  padding: 38px 30px;
  display: flex;
  align-items: center;
  justify-content: center;
}

.auth-card {
  width: min(420px, 100%);
  border-radius: var(--app-radius-md);
  border: 1px solid var(--app-card-border);
  background: var(--app-card-bg);
  box-shadow: var(--app-shadow-sm);
  padding: 32px 30px 24px;
}

.auth-head h2 {
  margin: 0;
  font-size: 26px;
  color: var(--app-text-primary);
  letter-spacing: 0.3px;
}

.auth-head p {
  margin: 8px 0 0;
  font-size: 13px;
  color: var(--app-text-muted);
}

.auth-mode-switch {
  margin-top: 14px;
  display: inline-flex;
  align-items: center;
  gap: 8px;
  padding: 4px;
  border: 1px solid var(--app-card-border);
  border-radius: 999px;
  background: #fff;
}

.auth-mode-btn {
  border: none;
  background: transparent;
  padding: 6px 14px;
  border-radius: 999px;
  font-size: 12px;
  font-weight: 600;
  color: var(--app-text-muted);
  cursor: pointer;
  transition: all 0.18s ease;
}

.auth-mode-btn.is-active {
  color: #1f4fc9;
  background: rgba(64, 158, 255, 0.14);
}

.login-form {
  margin-top: 24px;
}

.login-form :deep(.el-form-item) {
  margin-bottom: 16px;
}

.login-form :deep(.el-form-item__label) {
  padding-bottom: 6px;
  font-size: 12px;
  font-weight: 500;
  color: var(--app-text-secondary);
}

.login-form :deep(.el-input__wrapper) {
  border-radius: var(--app-radius-sm);
  min-height: 44px;
}

.login-btn {
  margin-top: 12px;
  width: 100%;
  height: 44px;
  border-radius: var(--app-radius-sm);
  font-size: 15px;
  font-weight: 600;
  letter-spacing: 0.3px;
}

.capability-list {
  margin: 18px 0 0;
  padding: 14px 0 0;
  list-style: none;
  border-top: 1px dashed var(--app-card-border);
  display: flex;
  flex-direction: column;
  gap: 9px;
}

.capability-list li {
  display: flex;
  align-items: flex-start;
  gap: 8px;
  font-size: 12px;
  line-height: 1.5;
  color: var(--app-text-secondary);
}

.capability-list .el-icon {
  margin-top: 1px;
  color: #409eff;
}

@media (max-width: 1040px) {
  .login-layout {
    grid-template-columns: 1fr;
    max-width: 560px;
  }

  .visual-panel {
    border-right: none;
    border-bottom: 1px solid var(--app-card-border);
    padding: 32px 30px;
  }

  .headline {
    margin-top: 24px;
    font-size: 28px;
  }

  .data-cards {
    margin-top: 24px;
  }
}

@media (max-width: 768px) {
  .login-page {
    padding: 14px;
  }

  .visual-panel {
    display: none;
  }

  .login-layout {
    display: block;
    width: min(430px, 100%);
  }

  .auth-panel {
    padding: 14px;
  }

  .auth-card {
    padding: 24px 20px 18px;
  }

  .auth-head h2 {
    font-size: 24px;
  }
}
</style>
