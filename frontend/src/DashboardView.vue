<script setup>
import { computed, onMounted, reactive, ref } from 'vue'
import axios from 'axios'
import {
  ElAlert,
  ElButton,
  ElDivider,
  ElDropdown,
  ElDropdownItem,
  ElDropdownMenu,
  ElIcon,
  ElInput,
  ElInputNumber,
  ElOption,
  ElProgress,
  ElSelect,
  ElTable,
  ElTableColumn,
  ElUpload,
  ElMessage,
} from 'element-plus'
import { ArrowDown, DataAnalysis, Download, Files, HomeFilled, Plus, Reading, School, Upload, User, UserFilled } from '@element-plus/icons-vue'

const api = axios.create({ baseURL: '/api/v1' })
api.interceptors.request.use(config => { const value = localStorage.getItem('token'); if (value) config.headers.Authorization = `Bearer ${value}`; return config })
const token = ref(localStorage.getItem('token'))
const user = ref(JSON.parse(localStorage.getItem('user') || 'null'))
const active = ref('overview'), loading = ref(false), error = ref('')
const rows = ref([])
const options = reactive({ departments: [], classes: [], courses: [], terms: [], students: [] })
const grade = reactive({ studentId: '', courseId: '', termId: '', credits: '', assessmentType: 'EXAM', score: '' })
const gradeFilters = reactive({ studentId: '', termId: '', classId: '', courseId: '', scoreMin: '', scoreMax: '', keyword: '' })
const accountFilters = reactive({ keyword: '', role: '', status: '' })
const fileState = reactive({ account: null, grade: null, accountUploading: false, gradeUploading: false, accountProgress: 0, gradeProgress: 0, accountReport: null, gradeReport: null, accountError: '', gradeError: '' })
const profile = reactive({ id: '', username: '', realName: '', email: '', phone: '', role: '', avatarUrl: null, createdAt: '' })
const avatarPreview = ref('')
const contactDraft = reactive({ email: '', phone: '' })
const passwordDraft = reactive({ currentPassword: '', newPassword: '' })
const passwordVerified = ref(false)
const availability = reactive({ username: null, email: null, phone: null })
const visibleAccounts = computed(() => rows.value.filter(row => {
  const query = accountFilters.keyword.trim().toLowerCase()
  const matchesText = !query || [row.business_no, row.username, row.real_name, row.email, row.phone].some(value => String(value || '').toLowerCase().includes(query))
  return matchesText && (!accountFilters.role || row.role === accountFilters.role) && (!accountFilters.status || row.status === accountFilters.status)
}))
const draft = reactive({ name: '', entryYear: new Date().getFullYear(), departmentId: '', classId: '', courseId: '', accountId: '', code: '', academicYear: '', termOrder: 1, defaultCredits: 3, studentNo: '', fullName: '', major: '', businessNo: '', username: '', realName: '', password: '' })
const menus = computed(() => ({ ADMIN: [['overview','概览',HomeFilled],['students','学生档案',Reading],['foundation','教学资料',School],['accounts','账户管理',User],['grades','成绩记录',Files]], TEACHER: [['overview','概览',HomeFilled],['grades','成绩录入',Files],['students','学生成绩',Reading]], COUNSELOR: [['overview','概览',HomeFilled],['students','班级成绩',Reading]], STUDENT: [['overview','概览',HomeFilled],['grades','我的成绩',Files]] }[user.value?.role] || []))
const title = computed(() => active.value === 'profile' ? '个人中心' : menus.value.find(item => item[0] === active.value)?.[1] || '概览')
const emit = defineEmits(['logout'])
async function logout(identifier = '') { try { if (token.value) await api.post('/auth/logout') } catch {} if (avatarPreview.value) URL.revokeObjectURL(avatarPreview.value); avatarPreview.value = ''; localStorage.removeItem('token'); localStorage.removeItem('user'); token.value = null; user.value = null; rows.value = []; emit('logout', identifier) }
async function loadView() { if (!token.value) return; loading.value = true; error.value = ''; try { if (active.value === 'profile') { const { data } = await api.get('/me/profile'); Object.assign(profile, data.data); contactDraft.email = data.data.email || ''; contactDraft.phone = data.data.phone || ''; if (avatarPreview.value) URL.revokeObjectURL(avatarPreview.value); avatarPreview.value = ''; if (data.data.avatarUrl) { const image = await api.get('/me/avatar', { responseType: 'blob' }); avatarPreview.value = URL.createObjectURL(image.data) } return } const { data } = await api.get('/scope/options'); Object.assign(options, data.data); if (user.value.role !== 'STUDENT') options.students = (await api.get('/students')).data.data.items; if (user.value.role === 'ADMIN') options.accounts = (await api.get('/admin/accounts')).data.data; if (active.value === 'students') rows.value = options.students; else if (active.value === 'foundation') rows.value = options.departments; else if (active.value === 'accounts') rows.value = options.accounts || []; else if (user.value.role === 'STUDENT') rows.value = (await api.get('/me/grades')).data.data.items; else { const params = Object.fromEntries(Object.entries(gradeFilters).filter(([, value]) => value !== '')); rows.value = (await api.get('/grades', { params })).data.data.items } } catch (e) { if (e.response?.status === 401) await logout(); else error.value = e.response?.data?.message || '数据暂时无法读取' } finally { loading.value = false } }
async function create(kind, body) { try { await api.post(`/admin/${kind}`, body); ElMessage.success('已保存'); await loadView() } catch (e) { ElMessage.error(e.response?.data?.message || '保存失败') } }
async function addGrade() { try { await api.post('/grades', grade); ElMessage.success('成绩已保存'); grade.score = ''; await loadView() } catch (e) { ElMessage.error(e.response?.data?.message || '录入失败') } }
async function assign(accountId, type, targetId) { try { await api.put(`/admin/accounts/${accountId}/${type}/${targetId}`); ElMessage.success('授权已添加'); await loadView() } catch (e) { ElMessage.error(e.response?.data?.message || '授权失败') } }
function chooseFile(kind, files) { fileState[kind] = files?.at(-1)?.raw || null; if (kind === 'account') { fileState.accountReport = null; fileState.accountError = '' } else { fileState.gradeReport = null; fileState.gradeError = '' } }
async function downloadXlsx(path, name, params) { try { const { data } = await api.get(path, { params, responseType: 'blob' }); const url = URL.createObjectURL(data); const link = document.createElement('a'); link.href = url; link.download = name; document.body.append(link); link.click(); link.remove(); setTimeout(() => URL.revokeObjectURL(url), 1000) } catch (e) { ElMessage.error(e.response?.data?.message || '文件下载失败') } }
async function importAccounts() { if (!fileState.account || !draft.password) return ElMessage.error('请选择工作簿并填写临时密码'); const body = new FormData(); body.append('file', fileState.account); body.append('temporaryPassword', draft.password); fileState.accountUploading = true; fileState.accountProgress = 0; fileState.accountError = ''; try { const { data } = await api.post('/admin/accounts/import', body, { onUploadProgress: event => { if (event.total) fileState.accountProgress = Math.round(event.loaded * 100 / event.total) } }); fileState.accountReport = data.data; ElMessage.success(`导入完成：新增 ${data.data.created}，跳过 ${data.data.skipped}，失败 ${data.data.failed}`); await loadView() } catch (e) { fileState.accountError = e.response?.data?.message || '账户导入失败'; ElMessage.error(fileState.accountError) } finally { fileState.accountUploading = false; draft.password = '' } }
async function importGrades() { if (!fileState.grade) return ElMessage.error('请选择工作簿'); const body = new FormData(); body.append('file', fileState.grade); fileState.gradeUploading = true; fileState.gradeProgress = 0; fileState.gradeReport = null; fileState.gradeError = ''; try { const { data } = await api.post('/grades/import', body, { onUploadProgress: event => { if (event.total) fileState.gradeProgress = Math.round(event.loaded * 100 / event.total) } }); fileState.gradeReport = data.data; ElMessage.success(`已导入 ${data.data.created} 条成绩`); await loadView() } catch (e) { fileState.gradeError = e.response?.data?.message || '成绩批次未写入'; ElMessage.error(fileState.gradeError) } finally { fileState.gradeUploading = false } }
async function saveContacts() { try { await api.patch('/me/contact', { email: contactDraft.email || null, phone: contactDraft.phone || null }); ElMessage.success('联系方式已更新'); await loadView() } catch (e) { ElMessage.error(e.response?.data?.message || '更新失败') } }
async function checkAvailability(field, value) { if (!value?.trim()) { availability[field] = null; return ElMessage.warning('先填写要检查的值') } try { const { data } = await api.get('/me/availability', { params: { field, value } }); availability[field] = data.data.available; ElMessage[data.data.available ? 'success' : 'warning'](data.data.available ? '可以使用' : '已被使用') } catch (e) { availability[field] = null; ElMessage.error(e.response?.data?.message || '检查失败') } }
async function changeUsername() { try { const { data } = await api.patch('/me/username', { username: profile.username }); if (data.data.requiresLogin) { ElMessage.success('用户名已更新，请使用新用户名重新登录'); await logout(profile.username) } else ElMessage.success('用户名未变化') } catch (e) { ElMessage.error(e.response?.data?.message || '用户名更新失败') } }
async function verifyCurrentPassword() { try { await api.post('/me/password-verifications', { password: passwordDraft.currentPassword }); passwordVerified.value = true; ElMessage.success('密码验证通过') } catch (e) { passwordVerified.value = false; ElMessage.error(e.response?.data?.message || '密码验证失败') } }
async function changePassword() { try { const { data } = await api.put('/me/password', { oldPassword: passwordDraft.currentPassword, newPassword: passwordDraft.newPassword }); if (data.data.requiresLogin) { ElMessage.success('密码已更新，请重新登录'); await logout() } } catch (e) { ElMessage.error(e.response?.data?.message || '密码更新失败') } }
async function uploadAvatar(file) { const body = new FormData(); body.append('file', file.raw); try { await api.post('/me/avatar', body); ElMessage.success('头像已更新'); await loadView() } catch (e) { ElMessage.error(e.response?.data?.message || '头像上传失败，原头像仍可使用') } }
async function removeAvatar() { try { await api.delete('/me/avatar'); ElMessage.success('已恢复默认头像'); await loadView() } catch (e) { ElMessage.error(e.response?.data?.message || '头像移除失败') } }
onMounted(async () => { if (token.value) { try { user.value = (await api.get('/me')).data.data; localStorage.setItem('user', JSON.stringify(user.value)); await loadView() } catch { await logout() } } })
</script>
<template>
  <div class="app-shell"><aside class="sidebar"><div class="brand"><span class="brand-icon small"><el-icon><Reading /></el-icon></span><span>学务系统</span></div><div class="workspace-label">工作区</div><nav><button v-for="item in menus" :key="item[0]" class="nav-item" :class="{selected:active===item[0]}" @click="active=item[0];loadView()"><el-icon><component :is="item[2]" /></el-icon><span>{{ item[1] }}</span></button></nav><div class="sidebar-bottom"><div class="connection"><i></i>本地服务</div><button class="nav-item" :class="{selected:active==='profile'}" @click="active='profile';loadView()"><el-icon><User /></el-icon><span>个人中心</span></button></div></aside>
    <section class="main-area"><header class="topbar"><div class="breadcrumb">工作台 <span>/</span> {{ title }}</div><el-dropdown><button class="account-menu"><span class="avatar"><el-icon><UserFilled /></el-icon></span><span>{{ user.realName }}</span><el-icon class="down"><ArrowDown /></el-icon></button><template #dropdown><el-dropdown-menu><el-dropdown-item @click="logout">退出登录</el-dropdown-item></el-dropdown-menu></template></el-dropdown></header>
      <main class="content"><div class="page-heading"><div><p class="eyebrow">{{ user.role }}</p><h1>{{ active==='overview'?`早上好，${user.realName}`:title }}</h1><p v-if="active==='overview'" class="subheading">学习与教学数据一览</p></div><el-button v-if="active==='grades'&&user.role!=='STUDENT'" type="primary" :icon="Plus" @click="document.getElementById('grade-entry')?.scrollIntoView({behavior:'smooth'})">录入成绩</el-button></div>
        <div v-if="error" class="error-banner">{{ error }} <el-button link type="primary" @click="loadView">重试</el-button></div><section v-if="active==='overview'" class="overview-strip"><div><span>可见成绩记录</span><strong>{{ rows.length }}</strong></div><div><span>当前角色</span><strong class="role-label">{{ user.role }}</strong></div><div><span>数据更新</span><strong class="role-label">实时查询</strong></div></section>
        <section v-if="active==='foundation'&&user.role==='ADMIN'" class="work-section"><div class="section-head"><div><h2>教学基础资料</h2><p>建立学院、班级、学期和课程后维护学生与授权。</p></div></div><div class="foundation-grid">
          <div class="entry-form"><h3>新增学院</h3><el-input v-model="draft.name" placeholder="学院名称"/><el-button type="primary" @click="create('departments',{name:draft.name})">添加学院</el-button></div>
          <div class="entry-form"><h3>新增班级</h3><el-input v-model="draft.name" placeholder="班级名称"/><el-select v-model="draft.departmentId" placeholder="所属学院"><el-option v-for="x in options.departments" :key="x.id" :label="x.name" :value="x.id"/></el-select><el-input-number v-model="draft.entryYear" :min="2000" :max="2100"/><el-button type="primary" @click="create('classes',{name:draft.name,entryYear:Number(draft.entryYear),departmentId:draft.departmentId})">添加班级</el-button></div>
          <div class="entry-form"><h3>新增学期</h3><el-input v-model="draft.code" placeholder="学期编码"/><el-input v-model="draft.name" placeholder="展示名称"/><el-input v-model="draft.academicYear" placeholder="学年，如 2026-2027"/><el-input-number v-model="draft.termOrder" :min="1" :max="3"/><el-button type="primary" @click="create('terms',{code:draft.code,name:draft.name,academicYear:draft.academicYear,termOrder:Number(draft.termOrder)})">添加学期</el-button></div>
          <div class="entry-form"><h3>新增课程</h3><el-input v-model="draft.name" placeholder="课程名称"/><el-select v-model="draft.departmentId" placeholder="所属学院"><el-option v-for="x in options.departments" :key="x.id" :label="x.name" :value="x.id"/></el-select><el-input-number v-model="draft.defaultCredits" :min="0" :precision="2"/><el-button type="primary" @click="create('courses',{name:draft.name,defaultCredits:Number(draft.defaultCredits),departmentId:draft.departmentId})">添加课程</el-button></div>
          <div class="entry-form"><h3>新增学生档案</h3><el-input v-model="draft.studentNo" placeholder="学号"/><el-input v-model="draft.fullName" placeholder="姓名"/><el-input v-model="draft.major" placeholder="专业"/><el-select v-model="draft.departmentId" placeholder="学院"><el-option v-for="x in options.departments" :key="x.id" :label="x.name" :value="x.id"/></el-select><el-select v-model="draft.classId" placeholder="班级"><el-option v-for="x in options.classes" :key="x.id" :label="x.name" :value="x.id"/></el-select><el-button type="primary" @click="create('students',{studentNo:draft.studentNo,fullName:draft.fullName,major:draft.major,departmentId:draft.departmentId,classId:draft.classId})">添加学生</el-button></div>
          <div class="entry-form"><h3>新增教师账户</h3><el-input v-model="draft.businessNo" placeholder="工号"/><el-input v-model="draft.username" placeholder="用户名"/><el-input v-model="draft.realName" placeholder="姓名"/><el-input v-model="draft.password" type="password" placeholder="初始密码" show-password/><el-button type="primary" @click="create('accounts',{role:'TEACHER',businessNo:draft.businessNo,username:draft.username,realName:draft.realName,password:draft.password})">创建教师</el-button></div>
          <div class="entry-form"><h3>教师课程授权</h3><el-select v-model="draft.accountId" placeholder="教师账户"><el-option v-for="x in options.accounts" :key="x.id" :label="`${x.business_no} · ${x.real_name}`" :value="x.id"/></el-select><el-select v-model="draft.courseId" placeholder="课程"><el-option v-for="x in options.courses" :key="x.id" :label="x.name" :value="x.id"/></el-select><el-button type="primary" @click="assign(draft.accountId,'course',draft.courseId)">分配课程</el-button><el-select v-model="draft.classId" placeholder="班级"><el-option v-for="x in options.classes" :key="x.id" :label="x.name" :value="x.id"/></el-select><el-button type="primary" @click="assign(draft.accountId,'class',draft.classId)">分配班级</el-button></div>
        </div></section>
        <section v-if="active==='accounts'&&user.role==='ADMIN'" class="work-section"><div class="section-head"><div><h2>账户文件</h2><p>导入结果按原始行号返回。</p></div><div class="toolbar"><el-button :icon="Download" @click="downloadXlsx('/admin/accounts/import-template','accounts-import-template.xlsx')">下载模板</el-button><el-button :icon="Download" @click="downloadXlsx('/admin/accounts/export','accounts.xlsx',accountFilters)">导出账户</el-button></div></div><div class="filter-row"><el-input v-model="accountFilters.keyword" placeholder="学号、工号、用户名或姓名" clearable/><el-select v-model="accountFilters.role" placeholder="全部角色" clearable><el-option label="全部角色" value=""/><el-option label="管理员" value="ADMIN"/><el-option label="教师" value="TEACHER"/><el-option label="辅导员" value="COUNSELOR"/><el-option label="学生" value="STUDENT"/></el-select><el-select v-model="accountFilters.status" placeholder="全部状态" clearable><el-option label="全部状态" value=""/><el-option label="启用" value="ACTIVE"/><el-option label="禁用" value="DISABLED"/></el-select></div><div class="file-workflow"><el-upload drag :auto-upload="false" :show-file-list="false" accept=".xlsx" :on-change="file => chooseFile('account',[file])"><el-icon class="upload-icon"><Upload /></el-icon><div>{{ fileState.account?.name || '选择账户工作簿' }}</div></el-upload><el-input v-model="draft.password" type="password" show-password autocomplete="new-password" placeholder="为新账户设置临时密码"/><el-button type="primary" :loading="fileState.accountUploading" :disabled="!fileState.account||!draft.password" @click="importAccounts">导入账户</el-button></div><el-progress v-if="fileState.accountUploading" :percentage="fileState.accountProgress" :show-text="false"/><el-alert v-if="fileState.accountError" :title="fileState.accountError" type="error" :closable="false" show-icon/><div v-if="fileState.accountReport" class="import-summary">共 {{ fileState.accountReport.total }} 行 · 新增 {{ fileState.accountReport.created }} · 跳过 {{ fileState.accountReport.skipped }} · 失败 {{ fileState.accountReport.failed }}</div><el-table v-if="fileState.accountReport?.rows?.length" :data="fileState.accountReport.rows" size="small"><el-table-column prop="sourceRow" label="源行" width="90"/><el-table-column prop="code" label="结果" width="210"/><el-table-column prop="message" label="说明"/></el-table></section>
        <section v-if="active==='grades'&&user.role!=='STUDENT'" class="work-section"><div class="section-head"><div><h2>成绩文件</h2><p>批次中的任一错误都会阻止整批写入。</p></div><div class="toolbar"><el-button :icon="Download" @click="downloadXlsx('/grades/import-template','grades-import-template.xlsx')">下载模板</el-button><el-button :icon="Download" @click="downloadXlsx('/grades/export','grades.xlsx',gradeFilters)">导出筛选结果</el-button></div></div><div class="filter-row grade-filters"><el-input v-model="gradeFilters.keyword" placeholder="学号、姓名或课程" clearable/><el-select v-model="gradeFilters.termId" placeholder="全部学期" clearable><el-option label="全部学期" value=""/><el-option v-for="x in options.terms" :key="x.id" :label="x.name" :value="String(x.id)"/></el-select><el-select v-model="gradeFilters.courseId" placeholder="全部课程" clearable><el-option label="全部课程" value=""/><el-option v-for="x in options.courses" :key="x.id" :label="x.name" :value="String(x.id)"/></el-select><el-select v-if="user.role==='ADMIN'" v-model="gradeFilters.classId" placeholder="全部班级" clearable><el-option label="全部班级" value=""/><el-option v-for="x in options.classes" :key="x.id" :label="x.name" :value="String(x.id)"/></el-select><el-input v-model="gradeFilters.scoreMin" type="number" placeholder="最低分"/><el-input v-model="gradeFilters.scoreMax" type="number" placeholder="最高分"/><el-button @click="loadView">筛选</el-button></div><div class="file-workflow"><el-upload drag :auto-upload="false" :show-file-list="false" accept=".xlsx" :on-change="file => chooseFile('grade',[file])"><el-icon class="upload-icon"><Upload /></el-icon><div>{{ fileState.grade?.name || '选择成绩工作簿' }}</div></el-upload><el-button type="primary" :loading="fileState.gradeUploading" :disabled="!fileState.grade" @click="importGrades">导入成绩</el-button></div><el-progress v-if="fileState.gradeUploading" :percentage="fileState.gradeProgress" :show-text="false"/><el-alert v-if="fileState.gradeError" :title="fileState.gradeError" type="error" :closable="false" show-icon/><div v-if="fileState.gradeReport" class="import-summary">已导入 {{ fileState.gradeReport.created }} 条成绩</div></section>
        <section v-if="active==='grades'&&user.role!=='STUDENT'" id="grade-entry" class="work-section"><div class="section-head"><div><h2>录入成绩</h2><p>成绩和派生学期汇总在同一数据库事务内更新。</p></div></div><div class="grade-entry"><el-select v-model="grade.studentId" filterable placeholder="学生"><el-option v-for="x in options.students" :key="x.id" :label="`${x.student_no} · ${x.full_name}`" :value="String(x.id)"/></el-select><el-select v-model="grade.courseId" placeholder="课程"><el-option v-for="x in options.courses" :key="x.id" :label="x.name" :value="String(x.id)"/></el-select><el-select v-model="grade.termId" placeholder="学期"><el-option v-for="x in options.terms" :key="x.id" :label="x.name" :value="String(x.id)"/></el-select><el-select v-model="grade.assessmentType"><el-option label="考试" value="EXAM"/><el-option label="考查" value="ASSESSMENT"/></el-select><el-input-number v-model="grade.credits" :min="0" :precision="2" placeholder="学分"/><el-input-number v-model="grade.score" :min="0" :max="100" :precision="2" placeholder="成绩"/><el-button type="primary" @click="addGrade">保存成绩</el-button></div></section>
        <section v-if="active==='profile'" class="work-section">
          <div class="section-head">
            <div><h2>个人资料</h2><p>{{ profile.realName }} · {{ profile.role }}</p></div>
            <el-upload :auto-upload="false" :show-file-list="false" accept="image/jpeg,image/png,image/gif,image/webp" :on-change="uploadAvatar">
              <el-button type="primary">更换头像</el-button>
            </el-upload>
          </div>
          <div class="avatar-profile">
            <img v-if="avatarPreview" :src="avatarPreview" alt="当前头像" />
            <span v-else class="avatar-fallback"><el-icon><UserFilled /></el-icon></span>
            <div>
              <strong>{{ profile.realName }}</strong><span>{{ profile.username }} · {{ profile.role }}</span>
              <el-button v-if="profile.avatarUrl" link type="danger" @click="removeAvatar">恢复默认头像</el-button>
            </div>
          </div>
          <div class="profile-grid">
            <div class="profile-field">
              <label>用户名</label>
              <el-input v-model="profile.username" aria-label="用户名" maxlength="30" />
              <div class="inline-actions">
                <el-button type="primary" @click="changeUsername">更新用户名</el-button>
                <el-button @click="checkAvailability('username',profile.username)">检查占用</el-button>
                <span v-if="availability.username!==null" :class="availability.username?'available':'unavailable'">{{ availability.username?'可用':'已占用' }}</span>
              </div>
            </div>
            <div class="profile-field">
              <label>邮箱</label>
              <el-input v-model="contactDraft.email" aria-label="邮箱" type="email" clearable />
              <div class="inline-actions">
                <el-button @click="checkAvailability('email',contactDraft.email)">检查占用</el-button>
                <span v-if="availability.email!==null" :class="availability.email?'available':'unavailable'">{{ availability.email?'可用':'已占用' }}</span>
              </div>
            </div>
            <div class="profile-field">
              <label>手机号</label>
              <el-input v-model="contactDraft.phone" aria-label="手机号" maxlength="11" clearable />
              <div class="inline-actions">
                <el-button @click="checkAvailability('phone',contactDraft.phone)">检查占用</el-button>
                <span v-if="availability.phone!==null" :class="availability.phone?'available':'unavailable'">{{ availability.phone?'可用':'已占用' }}</span>
              </div>
            </div>
            <div class="profile-field">
              <label>账户创建时间</label>
              <el-input :model-value="profile.createdAt" aria-label="账户创建时间" readonly />
            </div>
            <div class="profile-actions"><el-button type="primary" @click="saveContacts">保存联系方式</el-button></div>
          </div>
          <el-divider />
          <div class="password-section">
            <div><h3>密码与登录</h3><p>改密后需要使用新凭据重新登录。</p></div>
            <div class="password-grid">
              <el-input v-model="passwordDraft.currentPassword" aria-label="当前密码" type="password" show-password autocomplete="current-password" placeholder="当前密码" @input="passwordVerified=false" />
              <el-button :disabled="!passwordDraft.currentPassword" @click="verifyCurrentPassword">验证密码</el-button>
              <el-input v-model="passwordDraft.newPassword" aria-label="新密码" type="password" show-password autocomplete="new-password" placeholder="新密码" />
              <el-button type="primary" :disabled="!passwordVerified||!passwordDraft.currentPassword||!passwordDraft.newPassword" @click="changePassword">更新密码</el-button>
            </div>
          </div>
        </section>
        <section v-if="active!=='profile'" class="work-section">
          <div class="section-head">
            <div><h2>{{ active==='students'?'学生档案':active==='foundation'?'学院列表':active==='accounts'?'账户列表':user.role==='STUDENT'?'我的成绩':active==='grades'?'成绩记录':'可见记录' }}</h2><p>{{ active==='accounts'?visibleAccounts.length:rows.length }} 条记录</p></div>
            <el-button :icon="DataAnalysis" circle plain aria-label="刷新" @click="loadView"/>
          </div>
          <el-table v-loading="loading" :data="active==='accounts'?visibleAccounts:rows" stripe empty-text="暂无数据" class="data-table">
            <el-table-column v-if="active==='accounts'" prop="business_no" label="学号 / 工号" min-width="130"/>
            <el-table-column v-if="active==='accounts'" prop="username" label="用户名" min-width="120"/>
            <el-table-column v-if="active==='accounts'" prop="real_name" label="姓名" min-width="110"/>
            <el-table-column v-if="active==='accounts'" prop="email" label="邮箱" min-width="160"/>
            <el-table-column v-if="active==='accounts'" prop="phone" label="手机号" min-width="120"/>
            <el-table-column v-if="active==='accounts'" prop="role" label="角色" width="120"/>
            <el-table-column v-if="active==='accounts'" prop="status" label="状态" width="100"/>
            <el-table-column v-if="rows[0]?.student_no" prop="student_no" label="学号" min-width="120"/>
            <el-table-column v-if="rows[0]?.full_name" prop="full_name" label="学生" min-width="100"/>
            <el-table-column v-if="rows[0]?.class_name" prop="class_name" label="班级" min-width="120"/>
            <el-table-column v-if="rows[0]?.course_name" prop="course_name" label="课程" min-width="140"/>
            <el-table-column v-if="rows[0]?.term_name" prop="term_name" label="学期" min-width="130"/>
            <el-table-column v-if="rows[0]?.score_value!=null" prop="score_value" label="成绩" width="100"/>
            <el-table-column v-if="rows[0]?.grade_point!=null" prop="grade_point" label="GPA" width="90"/>
            <el-table-column v-if="rows[0]?.name" prop="name" label="名称"/>
            <el-table-column v-if="rows[0]?.student_count!=null" prop="student_count" label="人数" width="90"/>
          </el-table>
        </section><footer class="page-foot">学务成绩管理 <span>·</span> 本地工作区</footer>
      </main></section></div>
</template>
