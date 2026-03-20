<script setup lang="ts">
import { ref } from 'vue'
import { api } from '../api/api'
import { ElMessage } from 'element-plus'
import { useRoute, useRouter } from 'vue-router'

const username = ref('admin')
const password = ref('admin123')
const loading = ref(false)
const route = useRoute()
const router = useRouter()

async function onSubmit() {
  loading.value = true
  try {
    const result = await api.login(username.value, password.value)
    localStorage.setItem('token', result.token)
    localStorage.setItem('userInfo', JSON.stringify(result.userInfo))
    ElMessage.success(`登录成功，用户：${result.userInfo.displayName}`)
    const redirect = typeof route.query.redirect === 'string' ? route.query.redirect : '/dashboard'
    await router.replace(redirect)
  } catch (error) {
    ElMessage.error((error as Error).message)
  } finally {
    loading.value = false
  }
}
</script>

<template>
  <div class="login-page">
    <div class="left-banner">
      <h1>AI测试用例管理平台</h1>
      <p>需求资产、生成任务、测试用例一体化协同平台</p>
    </div>

    <el-card class="login-card" shadow="hover">
      <template #header>
        <div class="card-header">
          <strong>欢迎登录</strong>
          <span>请输入账号密码继续</span>
        </div>
      </template>
      <el-form class="login-form" label-position="top" @submit.prevent="onSubmit">
        <el-form-item label="用户名">
          <el-input v-model="username" size="large" placeholder="请输入用户名" />
        </el-form-item>
        <el-form-item label="密码">
          <el-input v-model="password" type="password" size="large" show-password placeholder="请输入密码" />
        </el-form-item>
        <div class="action-row">
          <el-button class="login-btn" type="primary" size="large" :loading="loading" @click="onSubmit">登录</el-button>
        </div>
      </el-form>
    </el-card>
  </div>
</template>

<style scoped>
.login-page {
  min-height: 100vh;
  display: grid;
  grid-template-columns: 1.2fr 1fr;
  background: linear-gradient(135deg, #eef4ff 0%, #f8fbff 60%, #ffffff 100%);
}

.left-banner {
  padding: 80px 70px;
  color: #1f2d3d;
  display: flex;
  flex-direction: column;
  justify-content: center;
}

.left-banner h1 {
  margin: 0 0 18px;
  font-size: 36px;
  line-height: 1.2;
}

.left-banner p {
  margin: 0;
  color: #5c6b7a;
  font-size: 16px;
}

.login-card {
  width: 420px;
  max-width: calc(100% - 40px);
  align-self: center;
  justify-self: center;
  border-radius: 14px;
}

.card-header {
  display: flex;
  flex-direction: column;
  gap: 8px;
}

.card-header span {
  color: #909399;
  font-size: 13px;
}

.login-btn {
  min-width: 132px;
}

.login-form {
  min-height: 320px;
  display: flex;
  flex-direction: column;
}

.action-row {
  margin-top: auto;
  display: flex;
  justify-content: flex-start;
}

@media (max-width: 980px) {
  .login-page {
    grid-template-columns: 1fr;
  }

  .left-banner {
    display: none;
  }
}
</style>
