import { createRouter, createWebHistory } from 'vue-router'
import DashboardView from '../views/DashboardView.vue'
import LoginView from '../views/LoginView.vue'
import ProjectsView from '../views/ProjectsView.vue'
import VersionsView from '../views/VersionsView.vue'
import OperationLogsView from '../views/OperationLogsView.vue'
import AssetsView from '../views/AssetsView.vue'
import AssetDetailView from '../views/AssetDetailView.vue'
import GenerationTasksView from '../views/GenerationTasksView.vue'
import TestCasesView from '../views/TestCasesView.vue'
import TestCaseDetailView from '../views/TestCaseDetailView.vue'
import ApiTestCasesView from '../views/ApiTestCasesView.vue'
import ApiTestCaseDetailView from '../views/ApiTestCaseDetailView.vue'
import ModelConfigsView from '../views/ModelConfigsView.vue'
import PromptTemplatesView from '../views/PromptTemplatesView.vue'
import ExportCenterView from '../views/ExportCenterView.vue'
import UiNlCasesView from '../views/UiNlCasesView.vue'
import UiNlTasksView from '../views/UiNlTasksView.vue'
import UiNlStepsView from '../views/UiNlStepsView.vue'
import UiNlTaskStepsDetailView from '../views/UiNlTaskStepsDetailView.vue'
import UiNlReportsView from '../views/UiNlReportsView.vue'

const router = createRouter({
  history: createWebHistory(),
  routes: [
    { path: '/', redirect: '/dashboard' },
    { path: '/login', component: LoginView, meta: { public: true, title: '登录' } },
    { path: '/dashboard', component: DashboardView, meta: { requiresAuth: true, title: '看板' } },
    { path: '/projects', component: ProjectsView, meta: { requiresAuth: true, title: '项目管理' } },
    { path: '/versions', component: VersionsView, meta: { requiresAuth: true, title: '版本管理' } },
    { path: '/assets', component: AssetsView, meta: { requiresAuth: true, title: '用例需求库' } },
    { path: '/assets/detail', component: AssetDetailView, meta: { requiresAuth: true, title: '资产详情' } },
    { path: '/generation-tasks', component: GenerationTasksView, meta: { requiresAuth: true, title: '用例任务中心' } },
    { path: '/ui-nl-cases', component: UiNlCasesView, meta: { requiresAuth: true, title: 'UI自然语言用例库' } },
    { path: '/ui-nl-tasks', component: UiNlTasksView, meta: { requiresAuth: true, title: 'UI自然语言任务中心' } },
    { path: '/ui-nl-steps', component: UiNlStepsView, meta: { requiresAuth: true, title: 'UI步骤管理' } },
    { path: '/ui-nl-steps/detail', component: UiNlTaskStepsDetailView, meta: { requiresAuth: true, title: '任务步骤详情' } },
    { path: '/ui-nl-reports', component: UiNlReportsView, meta: { requiresAuth: true, title: 'UI测试报告' } },
    { path: '/test-cases', component: TestCasesView, meta: { requiresAuth: true, title: '功能测试用例' } },
    { path: '/test-cases/detail', component: TestCaseDetailView, meta: { requiresAuth: true, title: '功能用例详情' } },
    { path: '/api-test-cases', component: ApiTestCasesView, meta: { requiresAuth: true, title: '接口测试用例' } },
    { path: '/api-test-cases/detail', component: ApiTestCaseDetailView, meta: { requiresAuth: true, title: '接口用例详情' } },
    { path: '/model-configs', component: ModelConfigsView, meta: { requiresAuth: true, title: '模型配置' } },
    { path: '/prompt-templates', component: PromptTemplatesView, meta: { requiresAuth: true, title: 'Prompt模板' } },
    { path: '/exports', component: ExportCenterView, meta: { requiresAuth: true, title: '导出中心' } },
    { path: '/operation-logs', component: OperationLogsView, meta: { requiresAuth: true, title: '操作日志' } },
  ],
})

router.beforeEach((to) => {
  const token = localStorage.getItem('token')
  const isAuthenticated = Boolean(token)

  if (to.meta.requiresAuth && !isAuthenticated) {
    return {
      path: '/login',
      query: { redirect: to.fullPath },
    }
  }

  if (to.path === '/login' && isAuthenticated) {
    const redirect = typeof to.query.redirect === 'string' ? to.query.redirect : '/dashboard'
    return redirect
  }

  return true
})

export default router
