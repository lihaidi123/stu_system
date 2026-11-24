<script setup>
import { computed, onBeforeUnmount, onMounted, reactive, ref } from 'vue'
import axios from 'axios'
import {
  ElButton, ElDialog, ElDropdown, ElDropdownItem, ElDropdownMenu, ElIcon,
  ElInput, ElInputNumber, ElMessage, ElOption, ElPagination, ElProgress, ElTooltip,
  ElSelect, ElTable, ElTableColumn, ElUpload,
} from 'element-plus'
import {
  ArrowDown, CircleCheckFilled, CircleCloseFilled, DataAnalysis, Download,
  Files, HomeFilled, MoreFilled, Plus, Reading, School, Upload, User, UserFilled, WarningFilled,
} from '@element-plus/icons-vue'

const api = axios.create({ baseURL: '/api/v1' })
api.interceptors.request.use(config => {
  const token = localStorage.getItem('token')
  if (token) config.headers.Authorization = `Bearer ${token}`
  return config
})

const emit = defineEmits(['logout'])
const user = ref(JSON.parse(localStorage.getItem('user') || 'null'))
const active = ref('overview')
const loading = ref(false)
const rows = ref([])
const analytics = ref(null)
const profile = reactive({ username: user.value?.username || '', realName: user.value?.realName || '', email: user.value?.email || '', phone: user.value?.phone || '', role: user.value?.role || '', avatarUrl: null, createdAt: '' })
const avatarUrl = ref('')
const options = reactive({ departments: [], classes: [], courses: [], terms: [], students: [], accounts: [] })
const paging = reactive({ page: 1, pageSize: 20, total: 0 })
const filters = reactive({ keyword: '', termId: '', classId: '', courseId: '', scoreMin: '', scoreMax: '' })
const accountFilter = reactive({ keyword: '', role: '', status: '' })
const studentFilter = reactive({ keyword: '', classId: '' })
const forms = reactive({
  grade: { studentId: '', courseId: '', termId: '', credits: null, assessmentType: 'EXAM', score: null },
  foundation: { department: '', departmentId: '', className: '', entryYear: new Date().getFullYear(), termCode: '', termName: '', academicYear: '', termOrder: 1, courseName: '', credits: 3 },
  account: { businessNo: '', username: '', realName: '', password: '', role: 'TEACHER' },
  student: { studentNo: '', fullName: '', major: '', departmentId: '', classId: '' },
  file: null,
  temporaryPassword: '',
})
const fileState = reactive({ uploading: false, progress: 0, report: null })
const notices = ref([])
const foundationDialog = reactive({ visible: false, kind: '' })
const password = reactive({ current: '', next: '', verified: false })
const contacts = reactive({ email: '', phone: '' })
const availability = reactive({ username: null, email: null, phone: null })
let requestId = 0
let studentSearchId = 0
let noticeSequence = 0
const noticeTimers = new Map()

const roleLabels = { ADMIN: '管理员', TEACHER: '教师', COUNSELOR: '辅导员', STUDENT: '学生' }
const menuMap = {
  ADMIN: [['overview', '概览', HomeFilled], ['accounts', '账户', User], ['students', '学生档案', Reading], ['foundation', '教学资料', School], ['grades', '成绩管理', Files]],
  TEACHER: [['overview', '概览', HomeFilled], ['grades', '成绩录入', Files], ['students', '学生成绩', Reading]],
  COUNSELOR: [['overview', '概览', HomeFilled], ['students', '班级学生', Reading], ['grades', '班级成绩', DataAnalysis]],
  STUDENT: [['overview', '概览', HomeFilled], ['grades', '我的成绩', Files]],
}
const menus = computed(() => menuMap[user.value?.role] || [])
const pageTitle = computed(() => ({ overview: '概览', accounts: '账户管理', students: user.value?.role === 'COUNSELOR' ? '班级学生' : '学生档案', foundation: '教学资料', grades: user.value?.role === 'STUDENT' ? '我的成绩' : user.value?.role === 'COUNSELOR' ? '班级成绩' : '成绩管理', profile: '个人中心' })[active.value])
const isAdmin = computed(() => user.value?.role === 'ADMIN')
const canWriteGrades = computed(() => ['ADMIN', 'TEACHER'].includes(user.value?.role))
const isStudent = computed(() => user.value?.role === 'STUDENT')
const accountRows = computed(() => rows.value.filter(row => {
  const q = accountFilter.keyword.trim().toLowerCase()
  const matches = !q || [row.business_no, row.username, row.real_name, row.email, row.phone].some(value => String(value || '').toLowerCase().includes(q))
  return matches && (!accountFilter.role || row.role === accountFilter.role) && (!accountFilter.status || row.status === accountFilter.status)
}))
const foundationDialogTitles = { department: '新增学院', class: '新增班级', term: '新增学期', course: '新增课程', student: '新增学生档案', staff: '创建教职工账户' }
const foundationDialogTitle = computed(() => foundationDialogTitles[foundationDialog.kind] || '新增资料')
const foundationActions = [
  ['department', '新增学院'], ['class', '新增班级'], ['term', '新增学期'],
  ['course', '新增课程'], ['student', '新增学生档案'], ['staff', '创建教职工账户'],
]
const noticeIcons = { error: CircleCloseFilled, success: CircleCheckFilled, warning: WarningFilled }

function notify(message, type = 'error') {
  const id = ++noticeSequence
  notices.value.push({ id, message: String(message), type })
  const timer = setTimeout(() => {
    notices.value = notices.value.filter(item => item.id !== id)
    noticeTimers.delete(id)
  }, 3000)
  noticeTimers.set(id, timer)
}

onBeforeUnmount(() => {
  for (const timer of noticeTimers.values()) clearTimeout(timer)
  noticeTimers.clear()
})

async function logOut(identifier = '', revoke = true) {
  if (revoke) {
    try { await api.post('/auth/logout') }
    catch { notify('本地登录状态已清除，服务端注销状态未能确认', 'warning') }
  }
  localStorage.removeItem('token')
  localStorage.removeItem('user')
  if (avatarUrl.value.startsWith('blob:')) URL.revokeObjectURL(avatarUrl.value)
  emit('logout', identifier)
}

function openView(key) {
  active.value = key
  paging.page = 1
  loadView()
}

async function loadView() {
  const token = localStorage.getItem('token')
  if (!token) return
  const id = ++requestId
  loading.value = true
  try {
    if (active.value === 'profile') {
      const { data } = await api.get('/me/profile')
      if (id !== requestId) return
      Object.assign(profile, data.data)
      contacts.email = data.data.email || ''
      contacts.phone = data.data.phone || ''
      await loadAvatar()
      return
    }
    const optionResponse = await api.get('/scope/options')
    if (id !== requestId) return
    Object.assign(options, optionResponse.data.data)
    if (isAdmin.value) options.accounts = (await api.get('/admin/accounts')).data.data
    if (active.value === 'overview') {
      const { data } = await api.get('/analytics/overview')
      if (id !== requestId) return
      analytics.value = data.data
      rows.value = []
      return
    }
    if (active.value === 'foundation') {
      rows.value = options.departments
      paging.total = rows.value.length
      return
    }
    if (active.value === 'accounts') {
      rows.value = options.accounts
      paging.total = rows.value.length
      return
    }
    if (active.value === 'students') {
      const path = isAdmin.value ? '/admin/students' : '/students'
      const studentParams = Object.fromEntries(Object.entries(studentFilter).filter(([, value]) => value !== ''))
      studentParams.page = paging.page
      studentParams.pageSize = paging.pageSize
      const { data } = await api.get(path, { params: studentParams })
      if (id !== requestId) return
      rows.value = data.data.items
      paging.total = data.data.total
      return
    }
    if (canWriteGrades.value) await searchStudents('')
    const params = Object.fromEntries(Object.entries(filters).filter(([, value]) => value !== ''))
    params.page = paging.page
    params.pageSize = paging.pageSize
    const { data } = await api.get(isStudent.value ? '/me/grades' : '/grades', { params })
    if (id !== requestId) return
    rows.value = data.data.items
    paging.total = data.data.total
  } catch (err) {
    if (id !== requestId) return
    if (err.response?.status === 401) logOut()
    else notify(err.response?.data?.message || '数据暂时无法读取')
  } finally {
    if (id === requestId) loading.value = false
  }
}
async function loadAvatar() {
  if (avatarUrl.value.startsWith('blob:')) URL.revokeObjectURL(avatarUrl.value)
  avatarUrl.value = ''
  if (!profile.avatarUrl) return
  const image = await api.get('/me/avatar', { responseType: 'blob' })
  avatarUrl.value = URL.createObjectURL(image.data)
}
async function searchStudents(keyword = '') {
  if (!canWriteGrades.value) return
  const searchId = ++studentSearchId
  try {
    const path = isAdmin.value ? '/admin/students' : '/students'
    const { data } = await api.get(path, { params: { page: 1, pageSize: 100, keyword: keyword || undefined } })
    if (searchId === studentSearchId) options.students = data.data.items
  } catch (err) {
    if (searchId === studentSearchId) notify(err.response?.data?.message || '学生选项暂时无法读取')
  }
}

async function run(action, successText, refresh = true) {
  try {
    await action()
    ElMessage.success(successText)
    if (refresh) await loadView()
    return true
  } catch (err) {
    notify(err.response?.data?.message || '操作失败')
    return false
  }
}

async function addGrade() {
  const body = { ...forms.grade }
  if (await run(() => api.post('/grades', body), '成绩已保存')) forms.grade.score = null
}

async function createDepartment() { return run(() => api.post('/admin/departments', { name: forms.foundation.department }), '学院已添加') }
async function createClass() { return run(() => api.post('/admin/classes', { name: forms.foundation.className, entryYear: forms.foundation.entryYear, departmentId: forms.foundation.departmentId }), '班级已添加') }
async function createTerm() { return run(() => api.post('/admin/terms', { code: forms.foundation.termCode, name: forms.foundation.termName, academicYear: forms.foundation.academicYear, termOrder: forms.foundation.termOrder }), '学期已添加') }
async function createCourse() { return run(() => api.post('/admin/courses', { name: forms.foundation.courseName, defaultCredits: forms.foundation.credits, departmentId: forms.foundation.departmentId }), '课程已添加') }
async function createStudent() { return run(() => api.post('/admin/students', { studentNo: forms.student.studentNo, fullName: forms.student.fullName, major: forms.student.major, departmentId: forms.student.departmentId, classId: forms.student.classId }), '学生档案已添加') }
async function createStaff() { return run(() => api.post('/admin/accounts', { businessNo: forms.account.businessNo, username: forms.account.username, realName: forms.account.realName, password: forms.account.password, role: forms.account.role }), '账户已创建') }

function openFoundationDialog(kind) {
  foundationDialog.kind = kind
  foundationDialog.visible = true
}

function clearFoundationPassword() {
  forms.account.password = ''
}

async function submitFoundationDialog() {
  const submitters = { department: createDepartment, class: createClass, term: createTerm, course: createCourse, student: createStudent, staff: createStaff }
  const submit = submitters[foundationDialog.kind]
  if (submit && await submit()) foundationDialog.visible = false
}

function onFileChange(file) {
  forms.file = file?.raw || null
  fileState.report = null
}

async function downloadFile(path, name, params) {
  try {
    const { data } = await api.get(path, { params, responseType: 'blob' })
    const url = URL.createObjectURL(data)
    const link = document.createElement('a')
    link.href = url
    link.download = name
    link.click()
    setTimeout(() => URL.revokeObjectURL(url), 1000)
  } catch (err) { notify(err.response?.data?.message || '下载失败') }
}

async function importFile() {
  if (!forms.file) return
  const form = new FormData()
  form.append('file', forms.file)
  const path = isAdmin.value ? '/admin/accounts/import' : '/grades/import'
  if (isAdmin.value) form.append('temporaryPassword', forms.temporaryPassword)
  fileState.uploading = true
  try {
    const { data } = await api.post(path, form, { onUploadProgress: e => { if (e.total) fileState.progress = Math.round(e.loaded * 100 / e.total) } })
    fileState.report = data.data
    ElMessage.success('导入完成')
    await loadView()
  } catch (err) {
    notify(err.response?.data?.message || '导入失败')
  } finally { fileState.uploading = false; if (isAdmin.value) forms.temporaryPassword = '' }
}

async function saveContacts() { await run(() => api.patch('/me/contact', { email: contacts.email || null, phone: contacts.phone || null }), '联系方式已更新') }
async function checkAvailability(field, value) {
  if (!value?.trim()) return notify('请先填写要检查的内容', 'warning')
  try {
    const { data } = await api.get('/me/availability', { params: { field, value } })
    availability[field] = data.data.available
  } catch (err) { notify(err.response?.data?.message || '检查失败') }
}
async function changeUsername() {
  try {
    const { data } = await api.patch('/me/username', { username: profile.username })
    if (data.data.requiresLogin) logOut(profile.username, false)
  } catch (err) { notify(err.response?.data?.message || '用户名更新失败') }
}
async function verifyPassword() {
  try { await api.post('/me/password-verifications', { password: password.current }); password.verified = true; ElMessage.success('密码验证通过') }
  catch (err) { password.verified = false; notify(err.response?.data?.message || '密码验证失败') }
}
async function changePassword() {
  try {
    await api.put('/me/password', { oldPassword: password.current, newPassword: password.next })
    logOut('', false)
  } catch (err) { notify(err.response?.data?.message || '密码更新失败') }
}
async function updateAvatar(file) {
  const body = new FormData()
  body.append('file', file.raw)
  await run(() => api.post('/me/avatar', body), '头像已更新')
}
async function removeAvatar() { await run(() => api.delete('/me/avatar'), '默认头像已恢复') }

onMounted(async () => {
  if (!localStorage.getItem('token')) return
  try {
    user.value = (await api.get('/me')).data.data
    localStorage.setItem('user', JSON.stringify(user.value))
    Object.assign(profile, user.value)
    await loadAvatar()
    await loadView()
  } catch (err) {
    notify(err.response?.data?.message || '当前登录状态无法读取，请重新登录')
    logOut()
  }
})
</script>

<template>
  <div class="new-shell">
    <aside class="new-sidebar">
      <a class="new-brand" href="#overview" @click.prevent="openView('overview')">
        <span class="brand-mark"><el-icon><Reading /></el-icon></span>
        <span>学务管理</span>
      </a>
      <p class="nav-caption">工作区</p>
      <nav class="new-nav" aria-label="主导航">
        <button v-for="item in menus" :key="item[0]" class="new-nav-item" :class="{ active: active === item[0] }" :aria-current="active === item[0] ? 'page' : undefined" @click="openView(item[0])">
          <el-icon aria-hidden="true"><component :is="item[2]" /></el-icon><span>{{ item[1] }}</span>
        </button>
      </nav>
      <div class="sidebar-bottom">
        <div class="sidebar-account">
          <button class="new-nav-item account-profile" :class="{ active: active === 'profile' }" @click="openView('profile')"><el-icon><User /></el-icon><span>{{ user?.realName || user?.username || '个人中心' }}</span></button>
        </div>
      </div>
    </aside>

    <div class="new-main">
      <main class="new-content">
        <div class="new-page-title">
          <h1>{{ pageTitle }}</h1>
          <div class="page-actions">
            <el-button v-if="active === 'grades' && canWriteGrades" type="primary" :icon="Plus" @click="document.getElementById('grade-entry')?.scrollIntoView({ behavior: 'smooth' })">新增成绩</el-button>
            <el-dropdown class="account-dropdown" popper-class="account-dropdown-popper">
              <button class="header-avatar" aria-label="账户头像菜单"><img v-if="avatarUrl" :src="avatarUrl" alt=""/><el-icon v-else><UserFilled/></el-icon><span class="avatar-caret"><el-icon><ArrowDown/></el-icon></span></button>
              <template #dropdown><el-dropdown-menu><el-dropdown-item @click="logOut()">退出登录</el-dropdown-item></el-dropdown-menu></template>
            </el-dropdown>
          </div>
        </div>
        <template v-if="active === 'overview'">
          <section class="metrics-row" aria-label="成绩统计" v-loading="loading">
            <div class="metric"><span>成绩记录</span><strong>{{ analytics?.recordCount ?? '—' }}</strong></div>
            <div class="metric"><span>加权平均</span><strong>{{ analytics?.weightedAverage ?? '—' }}</strong></div>
            <div class="metric"><span>GPA</span><strong>{{ analytics?.gpa ?? '—' }}</strong></div>
            <div class="metric"><span>及格率</span><strong>{{ analytics?.passRate == null ? '—' : `${analytics.passRate}%` }}</strong></div>
          </section>
          <div class="overview-grid">
            <section class="overview-detail">
              <div class="detail-heading"><div><h2>成绩区间</h2><span>当前可见范围</span></div><el-icon class="detail-icon"><DataAnalysis /></el-icon></div>
              <el-table :data="analytics?.scoreDistribution || []" size="small" v-loading="loading" empty-text="暂无成绩数据">
                <el-table-column prop="range" label="分数区间" />
                <el-table-column prop="count" label="记录数" width="120" align="right" />
              </el-table>
            </section>
            <section class="overview-detail">
              <div class="detail-heading"><div><h2>课程概况</h2><span>按课程汇总</span></div><el-icon class="detail-icon"><Reading /></el-icon></div>
              <el-table :data="analytics?.courses || []" size="small" v-loading="loading" empty-text="暂无课程数据">
                <el-table-column prop="courseName" label="课程" min-width="160" show-overflow-tooltip />
                <el-table-column prop="averageScore" label="平均分" width="100" align="right" />
                <el-table-column prop="recordCount" label="记录数" width="100" align="right" />
              </el-table>
            </section>
          </div>
        </template>

        <template v-if="active === 'foundation' && isAdmin">
          <section class="foundation-actions-panel">
            <div class="panel-heading"><h2>新增教学资料</h2></div>
            <div class="foundation-actions-row">
              <div class="foundation-actions" aria-label="新增教学资料类型">
                <el-button v-for="[kind, label] in foundationActions" :key="kind" @click="openFoundationDialog(kind)">{{ label }}</el-button>
              </div>
              <el-tooltip content="更多新增类型" placement="top">
                <el-dropdown trigger="click" popper-class="workspace-dropdown-popper">
                  <button type="button" class="foundation-more" aria-label="更多新增类型"><el-icon><MoreFilled/></el-icon></button>
                  <template #dropdown><el-dropdown-menu><el-dropdown-item v-for="[kind, label] in foundationActions" :key="kind" @click="openFoundationDialog(kind)">{{ label }}</el-dropdown-item></el-dropdown-menu></template>
                </el-dropdown>
              </el-tooltip>
            </div>
          </section>
          <section class="table-panel foundation-directory"><div class="panel-heading"><h2>学院目录</h2><span>{{ options.departments.length }} 项</span></div><el-table :data="options.departments" v-loading="loading" empty-text="暂无学院"><el-table-column prop="name" label="学院名称"/><el-table-column prop="id" label="编号" width="140"/></el-table></section>
        </template>

        <section v-if="active === 'accounts' && isAdmin" class="table-panel">
          <div class="panel-heading"><h2>账户目录</h2><div class="toolbar-actions"><span>{{ accountRows.length }} 个账户</span><el-button @click="downloadFile('/admin/accounts/import-template','accounts-import-template.xlsx')"><el-icon><Download/></el-icon>下载模板</el-button><el-button @click="downloadFile('/admin/accounts/export','accounts.xlsx',accountFilter)"><el-icon><Download/></el-icon>导出账户</el-button></div></div>
          <div class="filter-toolbar">
            <el-input v-model="accountFilter.keyword" clearable placeholder="搜索学号、工号、用户名或姓名" @keyup.enter="loadView" />
            <el-select v-model="accountFilter.role" clearable placeholder="全部角色"><el-option label="管理员" value="ADMIN"/><el-option label="教师" value="TEACHER"/><el-option label="辅导员" value="COUNSELOR"/><el-option label="学生" value="STUDENT"/></el-select>
            <el-select v-model="accountFilter.status" clearable placeholder="全部状态"><el-option label="启用" value="ACTIVE"/><el-option label="禁用" value="DISABLED"/></el-select>
          </div>
          <el-table :data="accountRows" v-loading="loading" stripe empty-text="没有匹配的账户">
            <el-table-column prop="business_no" label="学号 / 工号" min-width="130"/><el-table-column prop="real_name" label="姓名" min-width="110"/><el-table-column prop="username" label="用户名" min-width="130"/><el-table-column prop="email" label="邮箱" min-width="170" show-overflow-tooltip/><el-table-column prop="phone" label="手机号" min-width="130"/><el-table-column label="角色" width="110"><template #default="scope">{{ roleLabels[scope.row.role] || scope.row.role }}</template></el-table-column><el-table-column prop="status" label="状态" width="100"/>
          </el-table>
          <div class="file-row"><el-upload :auto-upload="false" :show-file-list="false" accept=".xlsx" :on-change="onFileChange"><el-button><el-icon><Upload/></el-icon>{{ forms.file?.name || '选择账户工作簿' }}</el-button></el-upload><el-input v-model="forms.temporaryPassword" type="password" show-password autocomplete="new-password" aria-label="新账户临时密码" placeholder="新账户临时密码"/><el-button type="primary" :disabled="!forms.file || !forms.temporaryPassword" :loading="fileState.uploading" @click="importFile">导入账户</el-button></div>
          <el-progress v-if="fileState.uploading" :percentage="fileState.progress" :show-text="false"/>
          <div v-if="fileState.report" class="import-result">共 {{ fileState.report.total }} 行 · 新增 {{ fileState.report.created }} · 跳过 {{ fileState.report.skipped }} · 失败 {{ fileState.report.failed }}</div>
        </section>

        <section v-if="active === 'students'" class="table-panel">
          <div class="panel-heading"><h2>{{ pageTitle }}</h2><span>{{ paging.total }} 条记录</span></div>
          <div class="filter-toolbar student-filters"><el-input v-model="studentFilter.keyword" clearable aria-label="搜索学生" placeholder="搜索学号或姓名" @keyup.enter="paging.page=1;loadView()"/><el-select v-model="studentFilter.classId" clearable aria-label="按班级筛选" placeholder="全部负责班级"><el-option v-for="item in options.classes" :key="item.id" :label="item.name" :value="item.id"/></el-select><el-button @click="paging.page=1;loadView()">筛选</el-button></div>
          <el-table :data="rows" v-loading="loading" stripe empty-text="当前范围内没有学生">
            <el-table-column prop="student_no" label="学号" min-width="145"/><el-table-column prop="full_name" label="姓名" min-width="140"/><el-table-column prop="class_name" label="班级" min-width="180"/><el-table-column v-if="isAdmin" prop="major" label="专业" min-width="180"/>
          </el-table>
          <el-pagination v-if="paging.total > paging.pageSize" v-model:current-page="paging.page" :page-size="paging.pageSize" :total="paging.total" layout="total, prev, pager, next" @current-change="loadView" />
        </section>

        <template v-if="active === 'grades'">
          <section v-if="canWriteGrades" class="table-panel grade-file-panel">
            <div class="panel-heading"><h2>成绩文件</h2><div class="toolbar-actions"><el-button @click="downloadFile('/grades/import-template', 'grades-import-template.xlsx')"><el-icon><Download /></el-icon>模板</el-button><el-button @click="downloadFile('/grades/export', 'grades.xlsx', filters)"><el-icon><Download /></el-icon>导出结果</el-button></div></div>
            <div class="file-row"><el-upload :auto-upload="false" :show-file-list="false" accept=".xlsx" :on-change="onFileChange"><el-button><el-icon><Upload /></el-icon>{{ forms.file?.name || '选择成绩工作簿' }}</el-button></el-upload><el-button type="primary" :disabled="!forms.file" :loading="fileState.uploading" @click="importFile">导入成绩</el-button></div>
            <el-progress v-if="fileState.uploading" :percentage="fileState.progress" :show-text="false" />
            <div v-if="fileState.report" class="import-result">已处理 {{ fileState.report.total }} 行 · 新增 {{ fileState.report.created }} · 跳过 {{ fileState.report.skipped }} · 失败 {{ fileState.report.failed }}</div>
          </section>
          <section v-if="canWriteGrades" id="grade-entry" class="entry-panel"><div class="panel-heading"><h2>录入成绩</h2></div>
            <div class="grade-form"><label>学生<el-select v-model="forms.grade.studentId" filterable remote :remote-method="searchStudents" placeholder="搜索并选择学生"><el-option v-for="item in options.students" :key="item.id" :label="`${item.student_no} · ${item.full_name}`" :value="item.id"/></el-select></label><label>课程<el-select v-model="forms.grade.courseId" placeholder="选择课程"><el-option v-for="item in options.courses" :key="item.id" :label="item.name" :value="item.id"/></el-select></label><label>学期<el-select v-model="forms.grade.termId" placeholder="选择学期"><el-option v-for="item in options.terms" :key="item.id" :label="item.name" :value="item.id"/></el-select></label><label>考核类型<el-select v-model="forms.grade.assessmentType"><el-option label="考试" value="EXAM"/><el-option label="考查" value="ASSESSMENT"/></el-select></label><label>成绩<el-input-number v-model="forms.grade.score" :min="0" :max="100" :precision="2"/></label><el-button type="primary" @click="addGrade">保存成绩</el-button></div>
          </section>
          <section class="table-panel">
            <div class="panel-heading"><h2>{{ isStudent ? '我的成绩' : '成绩记录' }}</h2><span>{{ paging.total }} 条记录</span></div>
            <div class="filter-toolbar grade-filters"><el-input v-if="!isStudent" v-model="filters.keyword" clearable aria-label="搜索成绩" placeholder="搜索学号、姓名或课程" @keyup.enter="paging.page=1;loadView()"/><el-select v-model="filters.termId" clearable aria-label="按学期筛选" placeholder="全部学期"><el-option v-for="item in options.terms" :key="item.id" :label="item.name" :value="item.id"/></el-select><el-select v-model="filters.courseId" v-if="!isStudent" clearable aria-label="按课程筛选" placeholder="全部课程"><el-option v-for="item in options.courses" :key="item.id" :label="item.name" :value="item.id"/></el-select><el-select v-model="filters.classId" v-if="!isStudent" clearable aria-label="按班级筛选" placeholder="全部负责班级"><el-option v-for="item in options.classes" :key="item.id" :label="item.name" :value="item.id"/></el-select><el-input v-if="!isStudent" v-model="filters.scoreMin" aria-label="最低分" type="number" placeholder="最低分"/><el-input v-if="!isStudent" v-model="filters.scoreMax" aria-label="最高分" type="number" placeholder="最高分"/><el-button @click="paging.page=1;loadView()">筛选</el-button></div>
            <el-table :data="rows" v-loading="loading" stripe empty-text="当前筛选条件下没有成绩">
              <el-table-column prop="student_no" label="学号" min-width="130" v-if="!isStudent"/><el-table-column prop="full_name" label="学生" min-width="120" v-if="!isStudent"/><el-table-column prop="course_name" label="课程" min-width="170"/><el-table-column prop="term_name" label="学期" min-width="150"/><el-table-column prop="credits_snapshot" label="学分" width="100" align="right"/><el-table-column prop="assessment_type" label="考核" width="100"><template #default="scope">{{ scope.row.assessment_type === 'EXAM' ? '考试' : '考查' }}</template></el-table-column><el-table-column prop="score_value" label="成绩" width="110" align="right"/><el-table-column prop="grade_point" label="GPA" width="100" align="right"/>
            </el-table>
            <el-pagination v-if="paging.total > paging.pageSize" v-model:current-page="paging.page" :page-size="paging.pageSize" :total="paging.total" layout="total, prev, pager, next" @current-change="loadView" />
          </section>
        </template>

        <section v-if="active === 'profile'" class="profile-layout">
          <section class="profile-panel"><div class="panel-heading"><h2>个人资料</h2><el-upload :auto-upload="false" :show-file-list="false" accept="image/jpeg,image/png,image/gif,image/webp" :on-change="updateAvatar"><el-button type="primary">更换头像</el-button></el-upload></div>
            <div class="profile-identity"><img v-if="avatarUrl" :src="avatarUrl" alt="当前头像"/><span v-else class="user-avatar large"><el-icon><UserFilled/></el-icon></span><div><strong>{{ profile.realName }}</strong><span>{{ profile.username }}</span><el-button v-if="profile.avatarUrl" link type="danger" @click="removeAvatar">移除头像</el-button></div></div>
            <div class="profile-fields"><label>用户名<el-input v-model="profile.username" aria-label="用户名" maxlength="30"/></label><div class="field-actions"><el-button @click="checkAvailability('username', profile.username)">检查占用</el-button><span v-if="availability.username !== null">{{ availability.username ? '可用' : '已占用' }}</span><el-button type="primary" @click="changeUsername">更新用户名</el-button></div><label>邮箱<el-input v-model="contacts.email" aria-label="邮箱" type="email"/></label><label>手机号<el-input v-model="contacts.phone" aria-label="手机号" maxlength="11"/></label><div class="field-actions"><el-button @click="checkAvailability('email', contacts.email)">检查邮箱</el-button><el-button @click="checkAvailability('phone', contacts.phone)">检查手机号</el-button><el-button type="primary" @click="saveContacts">保存联系方式</el-button></div></div>
          </section>
          <section class="profile-panel"><div class="panel-heading"><h2>密码与登录</h2></div><div class="password-fields"><label>当前密码<el-input v-model="password.current" aria-label="当前密码" type="password" show-password autocomplete="current-password" @input="password.verified=false"/></label><el-button :disabled="!password.current" @click="verifyPassword">验证密码</el-button><label>新密码<el-input v-model="password.next" aria-label="新密码" type="password" show-password autocomplete="new-password"/></label><el-button type="primary" :disabled="!password.verified || !password.next" @click="changePassword">更新密码</el-button></div></section>
        </section>
      </main>

      <el-dialog v-model="foundationDialog.visible" :title="foundationDialogTitle" width="520px" append-to-body :close-on-click-modal="false" class="foundation-dialog" @closed="clearFoundationPassword">
        <div class="dialog-fields">
          <label v-if="foundationDialog.kind === 'department'">学院名称<el-input v-model="forms.foundation.department" placeholder="例如：信息工程学院"/></label>

          <template v-if="foundationDialog.kind === 'class'">
            <label>班级名称<el-input v-model="forms.foundation.className" placeholder="班级名称"/></label>
            <label>所属学院<el-select v-model="forms.foundation.departmentId" placeholder="选择学院"><el-option v-for="item in options.departments" :key="item.id" :label="item.name" :value="item.id"/></el-select></label>
            <label>入学年份<el-input-number v-model="forms.foundation.entryYear" :min="2000" :max="2100"/></label>
          </template>

          <template v-if="foundationDialog.kind === 'term'">
            <label>学期编码<el-input v-model="forms.foundation.termCode" placeholder="如 2026-FALL"/></label>
            <label>学期名称<el-input v-model="forms.foundation.termName" placeholder="学期名称"/></label>
            <label>学年<el-input v-model="forms.foundation.academicYear" placeholder="2026-2027"/></label>
            <label>学期顺序<el-input-number v-model="forms.foundation.termOrder" :min="1" :max="3"/></label>
          </template>

          <template v-if="foundationDialog.kind === 'course'">
            <label>课程名称<el-input v-model="forms.foundation.courseName" placeholder="课程名称"/></label>
            <label>所属学院<el-select v-model="forms.foundation.departmentId" placeholder="选择学院"><el-option v-for="item in options.departments" :key="item.id" :label="item.name" :value="item.id"/></el-select></label>
            <label>默认学分<el-input-number v-model="forms.foundation.credits" :min="0" :precision="2"/></label>
          </template>

          <template v-if="foundationDialog.kind === 'student'">
            <label>学号<el-input v-model="forms.student.studentNo" placeholder="学号"/></label>
            <label>学生姓名<el-input v-model="forms.student.fullName" placeholder="姓名"/></label>
            <label>专业<el-input v-model="forms.student.major" placeholder="专业"/></label>
            <label>档案学院<el-select v-model="forms.student.departmentId" placeholder="选择学院"><el-option v-for="item in options.departments" :key="item.id" :label="item.name" :value="item.id"/></el-select></label>
            <label>档案班级<el-select v-model="forms.student.classId" placeholder="选择班级"><el-option v-for="item in options.classes" :key="item.id" :label="item.name" :value="item.id"/></el-select></label>
          </template>

          <template v-if="foundationDialog.kind === 'staff'">
            <label>账户业务号<el-input v-model="forms.account.businessNo" placeholder="工号"/></label>
            <label>账户用户名<el-input v-model="forms.account.username" placeholder="用户名"/></label>
            <label>姓名<el-input v-model="forms.account.realName" placeholder="姓名"/></label>
            <label>账户类型<el-select v-model="forms.account.role"><el-option label="教师" value="TEACHER"/><el-option label="辅导员" value="COUNSELOR"/></el-select></label>
            <label>初始密码<el-input v-model="forms.account.password" type="password" show-password autocomplete="new-password"/></label>
          </template>
        </div>
        <template #footer><el-button @click="foundationDialog.visible = false">取消</el-button><el-button type="primary" @click="submitFoundationDialog">保存</el-button></template>
      </el-dialog>
    </div>

    <div class="toast-stack" aria-live="assertive" aria-relevant="additions text">
      <TransitionGroup name="toast">
        <div v-for="(notice, index) in notices" :key="notice.id" class="toast-card" :class="`toast-${notice.type}`" :role="notice.type === 'error' ? 'alert' : 'status'" :style="{ '--toast-index': Math.min(index, 4) }">
          <el-icon aria-hidden="true"><component :is="noticeIcons[notice.type]"/></el-icon>
          <span>{{ notice.message }}</span>
        </div>
      </TransitionGroup>
    </div>
  </div>
</template>

<style src="./new-workspace.css"></style>
