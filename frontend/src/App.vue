<script setup>
import { defineAsyncComponent, reactive, ref } from 'vue'
import axios from 'axios'
import { ElButton, ElForm, ElFormItem, ElIcon, ElInput } from 'element-plus'
import { Reading } from '@element-plus/icons-vue'

const DashboardView = defineAsyncComponent(() => import('./DashboardView.vue'))
const api = axios.create({ baseURL: '/api/v1' })
const token = ref(localStorage.getItem('token'))
const loading = ref(false)
const error = ref('')
const form = reactive({ identifier: '', password: '' })

async function login() {
  loading.value = true
  error.value = ''
  try {
    const { data } = await api.post('/auth/login', form)
    localStorage.setItem('token', data.data.token)
    localStorage.setItem('user', JSON.stringify(data.data.user))
    token.value = data.data.token
  } catch {
    error.value = '账号或密码无效'
  } finally {
    loading.value = false
  }
}

function handleLogout(identifier = '') {
  token.value = null
  form.identifier = identifier
  form.password = ''
}
</script>

<template>
  <main v-if="!token" class="login-page">
    <section class="login-mark">
      <div class="brand-icon"><el-icon><Reading /></el-icon></div>
      <p class="eyebrow">STUDENT RECORDS</p>
      <h1>学务成绩管理</h1>
      <p class="login-note">统一查看教学数据与学习进展</p>
    </section>
    <el-form class="login-form" @submit.prevent="login">
      <h2>登录</h2>
      <p class="form-caption">用户名或学号 / 工号</p>
      <el-form-item>
        <el-input v-model="form.identifier" size="large" placeholder="用户名或学号 / 工号" autocomplete="username" />
      </el-form-item>
      <el-form-item>
        <el-input v-model="form.password" size="large" type="password" placeholder="密码" show-password autocomplete="current-password" @keyup.enter="login" />
      </el-form-item>
      <p v-if="error" class="form-error">{{ error }}</p>
      <el-button type="primary" size="large" class="login-submit" :loading="loading" @click="login">进入系统</el-button>
    </el-form>
  </main>
  <Suspense v-else>
    <component :is="DashboardView" @logout="handleLogout" />
    <template #fallback>
      <main class="workspace-loading" aria-live="polite">正在加载工作区…</main>
    </template>
  </Suspense>
</template>
