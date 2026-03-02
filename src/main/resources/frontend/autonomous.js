/**
 * SolonClaw 自主任务管理前端
 * 实现自主任务状态监控、任务管理、目标管理等功能
 */

// ==================== 配置 ====================
const CONFIG = {
    apiBase: 'http://localhost:8080/api',
    refreshInterval: 3000, // 3秒轮询
};

// ==================== 状态管理 ====================
const state = {
    currentTaskTab: 'pending',
    currentGoalTab: 'active',
    isRunning: false,
    data: {
        status: null,
        stats: null,
        tasks: {
            pending: [],
            executing: [],
            completed: []
        },
        goals: {
            active: [],
            completed: [],
            failed: []
        },
        resources: null,
        decision: null
    },
    refreshTimer: null
};

// ==================== 初始化 ====================
function init() {
    // 绑定事件
    bindEvents();

    // 初始加载数据
    refreshData();

    // 启动轮询
    startPolling();

    console.log('SolonClaw 自主任务管理系统已初始化');
}

// ==================== 事件绑定 ====================
function bindEvents() {
    // 创建目标表单
    document.getElementById('createGoalForm').addEventListener('submit', createGoal);
}

// ==================== 数据加载 ====================

/**
 * 刷新所有数据
 */
async function refreshData() {
    try {
        await Promise.all([
            loadStatus(),
            loadStats(),
            loadTasks(),
            loadGoals(),
            loadResources(),
            loadDecision()
        ]);
        updateLastUpdateTime();
    } catch (error) {
        console.error('刷新数据失败:', error);
        showToast('数据加载失败: ' + error.message, 'error');
    }
}

/**
 * 加载系统状态
 */
async function loadStatus() {
    try {
        const response = await fetch(`${CONFIG.apiBase}/autonomous/status`);
        const data = await response.json();

        if (data.code === 200) {
            state.data.status = data.data;
            state.isRunning = data.data.running || false;
            updateSystemStatus();
        }
    } catch (error) {
        console.error('加载状态失败:', error);
    }
}

/**
 * 加载统计信息
 */
async function loadStats() {
    try {
        const response = await fetch(`${CONFIG.apiBase}/autonomous/stats`);
        const data = await response.json();

        if (data.code === 200) {
            state.data.stats = data.data;
            updateStats();
        }
    } catch (error) {
        console.error('加载统计信息失败:', error);
    }
}

/**
 * 加载任务
 */
async function loadTasks() {
    try {
        const [pendingRes, executingRes, completedRes] = await Promise.all([
            fetch(`${CONFIG.apiBase}/autonomous/tasks/pending`),
            fetch(`${CONFIG.apiBase}/autonomous/tasks/executing`),
            fetch(`${CONFIG.apiBase}/autonomous/tasks/completed`)
        ]);

        const pendingData = await pendingRes.json();
        const executingData = await executingRes.json();
        const completedData = await completedRes.json();

        if (pendingData.code === 200) state.data.tasks.pending = pendingData.data || [];
        if (executingData.code === 200) state.data.tasks.executing = executingData.data || [];
        if (completedData.code === 200) state.data.tasks.completed = completedData.data || [];

        updateTaskCounts();
        renderTaskList();
    } catch (error) {
        console.error('加载任务失败:', error);
    }
}

/**
 * 加载目标
 */
async function loadGoals() {
    try {
        const [activeRes, completedRes] = await Promise.all([
            fetch(`${CONFIG.apiBase}/autonomous/goals/active`),
            fetch(`${CONFIG.apiBase}/autonomous/goals/completed`)
        ]);

        const activeData = await activeRes.json();
        const completedData = await completedRes.json();

        if (activeData.code === 200) state.data.goals.active = activeData.data || [];
        if (completedData.code === 200) state.data.goals.completed = completedData.data || [];

        updateGoalCounts();
        renderGoalList();
    } catch (error) {
        console.error('加载目标失败:', error);
    }
}

/**
 * 加载资源
 */
async function loadResources() {
    try {
        const response = await fetch(`${CONFIG.apiBase}/autonomous/resources`);
        const data = await response.json();

        if (data.code === 200) {
            state.data.resources = data.data;
            renderResources();
        }
    } catch (error) {
        console.error('加载资源失败:', error);
    }
}

/**
 * 加载决策状态
 */
async function loadDecision() {
    try {
        const response = await fetch(`${CONFIG.apiBase}/autonomous/decision`);
        const data = await response.json();

        if (data.code === 200) {
            state.data.decision = data.data;
            renderDecision();
        }
    } catch (error) {
        console.error('加载决策状态失败:', error);
    }
}

// ==================== 控制操作 ====================

/**
 * 启动自主运行系统
 */
async function startAutonomous() {
    try {
        const response = await fetch(`${CONFIG.apiBase}/autonomous/start`, {
            method: 'POST'
        });
        const data = await response.json();

        if (data.code === 200) {
            showToast('自主运行系统已启动', 'success');
            await refreshData();
        } else {
            showToast(data.message || '启动失败', 'error');
        }
    } catch (error) {
        console.error('启动失败:', error);
        showToast('启动失败: ' + error.message, 'error');
    }
}

/**
 * 停止自主运行系统
 */
async function stopAutonomous() {
    try {
        const response = await fetch(`${CONFIG.apiBase}/autonomous/stop`, {
            method: 'POST'
        });
        const data = await response.json();

        if (data.code === 200) {
            showToast('自主运行系统已停止', 'success');
            await refreshData();
        } else {
            showToast(data.message || '停止失败', 'error');
        }
    } catch (error) {
        console.error('停止失败:', error);
        showToast('停止失败: ' + error.message, 'error');
    }
}

/**
 * 手动触发任务
 */
async function triggerTask(taskId) {
    try {
        const response = await fetch(`${CONFIG.apiBase}/autonomous/tasks/${taskId}/trigger`, {
            method: 'POST'
        });
        const data = await response.json();

        if (data.code === 200) {
            showToast('任务已触发', 'success');
            await refreshData();
        } else {
            showToast(data.message || '触发失败', 'error');
        }
    } catch (error) {
        console.error('触发任务失败:', error);
        showToast('触发失败: ' + error.message, 'error');
    }
}

/**
 * 创建目标
 */
async function createGoal(event) {
    event.preventDefault();

    const title = document.getElementById('goalTitle').value.trim();
    const description = document.getElementById('goalDescription').value.trim();
    const priority = parseInt(document.getElementById('goalPriority').value);

    try {
        const response = await fetch(`${CONFIG.apiBase}/autonomous/goals`, {
            method: 'POST',
            headers: {
                'Content-Type': 'application/x-www-form-urlencoded',
            },
            body: `title=${encodeURIComponent(title)}&description=${encodeURIComponent(description)}&priority=${priority}`
        });
        const data = await response.json();

        if (data.code === 200) {
            showToast('目标创建成功', 'success');
            closeCreateGoalModal();
            document.getElementById('createGoalForm').reset();
            await refreshData();
        } else {
            showToast(data.message || '创建失败', 'error');
        }
    } catch (error) {
        console.error('创建目标失败:', error);
        showToast('创建失败: ' + error.message, 'error');
    }
}

/**
 * 完成目标
 */
async function completeGoal(goalId) {
    if (!confirm('确定要完成此目标吗？')) return;

    try {
        const response = await fetch(`${CONFIG.apiBase}/autonomous/goals/${goalId}/complete`, {
            method: 'POST'
        });
        const data = await response.json();

        if (data.code === 200) {
            showToast('目标已完成', 'success');
            await refreshData();
        } else {
            showToast(data.message || '操作失败', 'error');
        }
    } catch (error) {
        console.error('完成目标失败:', error);
        showToast('操作失败: ' + error.message, 'error');
    }
}

// ==================== UI 渲染 ====================

/**
 * 更新系统状态
 */
function updateSystemStatus() {
    const statusElement = document.getElementById('systemStatus');
    const runStatusElement = document.getElementById('runStatus');

    if (state.data.status && state.data.status.running) {
        statusElement.innerHTML = `
            <span class="w-2 h-2 bg-green-400 rounded-full pulse-dot"></span>
            <span class="text-sm">运行中</span>
        `;
        runStatusElement.textContent = '运行中';
        runStatusElement.className = 'px-2 py-1 text-xs rounded-full bg-green-100 text-green-600';
    } else {
        statusElement.innerHTML = `
            <span class="w-2 h-2 bg-gray-400 rounded-full"></span>
            <span class="text-sm">已停止</span>
        `;
        runStatusElement.textContent = '已停止';
        runStatusElement.className = 'px-2 py-1 text-xs rounded-full bg-gray-100 text-gray-600';
    }
}

/**
 * 更新统计信息
 */
function updateStats() {
    const stats = state.data.stats;
    if (!stats) return;

    // 更新任务统计
    if (stats.tasks) {
        const tasks = stats.tasks;
        const totalTasks = (tasks.pending || 0) + (tasks.executing || 0) + (tasks.completed || 0) + (tasks.failed || 0);
        document.getElementById('statTotalTasks').textContent = totalTasks;
        document.getElementById('statCompletedTasks').textContent = tasks.completed || 0;
    }

    // 更新目标统计
    if (stats.goals) {
        const goals = stats.goals;
        const totalGoals = (goals.active || 0) + (goals.completed || 0) + (goals.failed || 0);
        document.getElementById('statTotalGoals').textContent = totalGoals;
        document.getElementById('statCompletedGoals').textContent = goals.completed || 0;
    }
}

/**
 * 更新任务计数
 */
function updateTaskCounts() {
    document.getElementById('count-pending').textContent = state.data.tasks.pending.length;
    document.getElementById('count-executing').textContent = state.data.tasks.executing.length;
    document.getElementById('count-completed').textContent = state.data.tasks.completed.length;
}

/**
 * 更新目标计数
 */
function updateGoalCounts() {
    document.getElementById('goal-count-active').textContent = state.data.goals.active.length;
    document.getElementById('goal-count-completed').textContent = state.data.goals.completed.length;
}

/**
 * 更新最后更新时间
 */
function updateLastUpdateTime() {
    const now = new Date();
    const timeStr = now.toLocaleTimeString('zh-CN');
    document.getElementById('lastUpdateTime').textContent = `最后更新: ${timeStr}`;
}

/**
 * 渲染任务列表
 */
function renderTaskList() {
    const container = document.getElementById('taskContent');
    const tasks = state.data.tasks[state.currentTaskTab];

    if (!tasks || tasks.length === 0) {
        container.innerHTML = `
            <div class="flex flex-col items-center justify-center h-32 text-gray-400">
                <svg class="w-12 h-12 mb-2" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                    <path stroke-linecap="round" stroke-linejoin="round" stroke-width="1" d="M9 5H7a2 2 0 00-2 2v12a2 2 0 002 2h10a2 2 0 002-2V7a2 2 0 00-2-2h-2M9 5a2 2 0 002 2h2a2 2 0 002-2M9 5a2 2 0 012-2h2a2 2 0 012 2"></path>
                </svg>
                <span>暂无任务</span>
            </div>
        `;
        return;
    }

    let html = '<div class="space-y-3">';
    tasks.forEach(task => {
        html += renderTaskCard(task);
    });
    html += '</div>';
    container.innerHTML = html;
}

/**
 * 渲染任务卡片
 */
function renderTaskCard(task) {
    const statusColors = {
        PENDING: 'bg-yellow-100 text-yellow-700',
        EXECUTING: 'bg-blue-100 text-blue-700',
        COMPLETED: 'bg-green-100 text-green-700',
        FAILED: 'bg-red-100 text-red-700'
    };

    const statusLabels = {
        PENDING: '待执行',
        EXECUTING: '执行中',
        COMPLETED: '已完成',
        FAILED: '已失败'
    };

    const statusClass = statusColors[task.status] || 'bg-gray-100 text-gray-700';
    const statusLabel = statusLabels[task.status] || task.status;

    let actionButtons = '';
    if (task.status === 'PENDING') {
        actionButtons = `
            <button onclick="triggerTask('${task.id}')" class="text-blue-500 hover:text-blue-700 text-sm">
                执行
            </button>
        `;
    }

    return `
        <div class="bg-gray-50 rounded-lg p-3 card-hover">
            <div class="flex items-start justify-between">
                <div class="flex-1">
                    <div class="flex items-center space-x-2 mb-1">
                        <h3 class="font-medium text-gray-800">${escapeHtml(task.title || task.taskType || '未知任务')}</h3>
                        <span class="px-2 py-0.5 text-xs rounded-full ${statusClass}">${statusLabel}</span>
                    </div>
                    <p class="text-sm text-gray-600 mb-2">${escapeHtml(task.description || '无描述')}</p>
                    <div class="text-xs text-gray-400">
                        ID: ${task.id}
                        ${task.createdAt ? ` | 创建于: ${new Date(task.createdAt).toLocaleString('zh-CN')}` : ''}
                    </div>
                </div>
                ${actionButtons ? `<div>${actionButtons}</div>` : ''}
            </div>
        </div>
    `;
}

/**
 * 渲染目标列表
 */
function renderGoalList() {
    const container = document.getElementById('goalContent');
    const goals = state.data.goals[state.currentGoalTab];

    if (!goals || goals.length === 0) {
        container.innerHTML = `
            <div class="flex flex-col items-center justify-center h-32 text-gray-400">
                <svg class="w-12 h-12 mb-2" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                    <path stroke-linecap="round" stroke-linejoin="round" stroke-width="1" d="M5 3v4M3 5h4M6 17v4m-2-2h4m5-16l2.286 6.857L21 12l-5.714 2.143L13 21l-2.286-6.857L5 12l5.714-2.143L13 3z"></path>
                </svg>
                <span>暂无目标</span>
            </div>
        `;
        return;
    }

    let html = '<div class="space-y-3">';
    goals.forEach(goal => {
        html += renderGoalCard(goal);
    });
    html += '</div>';
    container.innerHTML = html;
}

/**
 * 渲染目标卡片
 */
function renderGoalCard(goal) {
    const progress = (goal.progress || 0) * 100;
    const priorityColors = {
        1: 'bg-gray-200',
        2: 'bg-blue-100',
        3: 'bg-blue-200',
        4: 'bg-green-100',
        5: 'bg-green-200',
        6: 'bg-yellow-100',
        7: 'bg-yellow-200',
        8: 'bg-orange-100',
        9: 'bg-orange-200',
        10: 'bg-red-100'
    };

    const priorityClass = priorityColors[goal.priority] || 'bg-gray-200';

    let actionButtons = '';
    if (state.currentGoalTab === 'active') {
        actionButtons = `
            <button onclick="completeGoal('${goal.id}')" class="text-blue-500 hover:text-blue-700 text-sm">
                完成
            </button>
        `;
    }

    return `
        <div class="bg-gray-50 rounded-lg p-3 card-hover">
            <div class="flex items-start justify-between mb-2">
                <div class="flex-1">
                    <div class="flex items-center space-x-2 mb-1">
                        <h3 class="font-medium text-gray-800">${escapeHtml(goal.title)}</h3>
                        <span class="px-2 py-0.5 text-xs rounded-full ${priorityClass} text-gray-700">
                            优先级: ${goal.priority}
                        </span>
                    </div>
                    <p class="text-sm text-gray-600">${escapeHtml(goal.description || '无描述')}</p>
                </div>
                ${actionButtons ? `<div>${actionButtons}</div>` : ''}
            </div>
            <div class="mb-1">
                <div class="flex items-center justify-between text-xs text-gray-500 mb-1">
                    <span>进度</span>
                    <span>${Math.round(progress)}%</span>
                </div>
                <div class="w-full bg-gray-200 rounded-full h-2">
                    <div class="progress-bar bg-blue-500 h-2 rounded-full" style="width: ${progress}%"></div>
                </div>
            </div>
            <div class="text-xs text-gray-400">
                ID: ${goal.id}
                ${goal.createdAt ? ` | 创建于: ${new Date(goal.createdAt).toLocaleString('zh-CN')}` : ''}
            </div>
        </div>
    `;
}

/**
 * 渲染资源列表
 */
function renderResources() {
    const container = document.getElementById('resourcesContent');
    const resources = state.data.resources;

    if (!resources || Object.keys(resources).length === 0) {
        container.innerHTML = `
            <div class="flex items-center justify-center h-24 text-gray-400 col-span-2">
                <span>暂无资源信息</span>
            </div>
        `;
        return;
    }

    let html = '';
    Object.entries(resources).forEach(([key, resource]) => {
        const available = resource.available !== undefined ? resource.available : true;
        const statusClass = available ? 'bg-green-100 border-green-300' : 'bg-red-100 border-red-300';
        const statusText = available ? '可用' : '不可用';
        const statusDot = available ? 'bg-green-500' : 'bg-red-500';

        html += `
            <div class="border rounded-lg p-3 ${statusClass}">
                <div class="flex items-center justify-between mb-2">
                    <h3 class="font-medium text-gray-800">${escapeHtml(key)}</h3>
                    <div class="flex items-center space-x-1">
                        <span class="w-2 h-2 rounded-full ${statusDot}"></span>
                        <span class="text-sm ${available ? 'text-green-700' : 'text-red-700'}">${statusText}</span>
                    </div>
                </div>
                ${resource.description ? `<p class="text-sm text-gray-600">${escapeHtml(resource.description)}</p>` : ''}
            </div>
        `;
    });

    container.innerHTML = html;
}

/**
 * 渲染决策引擎状态
 */
function renderDecision() {
    const container = document.getElementById('decisionContent');

    if (!state.data.decision) {
        container.innerHTML = `
            <div class="flex items-center justify-center h-24 text-gray-400">
                <span>暂无决策信息</span>
            </div>
        `;
        return;
    }

    const decision = state.data.decision;
    let html = `
        <div class="grid grid-cols-1 md:grid-cols-3 gap-4 w-full">
            <div class="bg-gray-50 rounded-lg p-3">
                <div class="text-sm text-gray-500">决策动作</div>
                <div class="text-lg font-semibold text-gray-800 mt-1">${escapeHtml(decision.action || '无')}</div>
            </div>
            <div class="bg-gray-50 rounded-lg p-3">
                <div class="text-sm text-gray-500">置信度</div>
                <div class="text-lg font-semibold text-blue-600 mt-1">${(decision.confidence || 0).toFixed(2)}</div>
            </div>
            <div class="bg-gray-50 rounded-lg p-3">
                <div class="text-sm text-gray-500">推理过程</div>
                <div class="text-sm text-gray-700 mt-1">${escapeHtml(decision.reasoning || '无')}</div>
            </div>
        </div>
    `;

    container.innerHTML = html;
}

// ==================== 交互函数 ====================

/**
 * 显示任务标签页
 */
function showTaskTab(tab) {
    state.currentTaskTab = tab;

    // 更新标签样式
    document.querySelectorAll('.tab-btn').forEach(btn => {
        btn.className = 'tab-btn py-3 px-2 border-b-2 border-transparent text-gray-500 hover:text-gray-700 font-medium';
    });
    document.getElementById(`tab-${tab}`).className = `tab-btn py-3 px-2 border-b-2 border-blue-500 text-blue-600 font-medium`;

    // 重新渲染
    renderTaskList();
}

/**
 * 显示目标标签页
 */
function showGoalTab(tab) {
    state.currentGoalTab = tab;

    // 更新标签样式
    document.querySelectorAll('.goal-tab-btn').forEach(btn => {
        btn.className = 'goal-tab-btn py-3 px-2 border-b-2 border-transparent text-gray-500 hover:text-gray-700 font-medium';
    });
    document.getElementById(`goal-tab-${tab}`).className = `goal-tab-btn py-3 px-2 border-b-2 border-green-500 text-green-600 font-medium`;

    // 重新渲染
    renderGoalList();
}

/**
 * 打开创建目标模态框
 */
function openCreateGoalModal() {
    document.getElementById('createGoalModal').classList.remove('hidden');
}

/**
 * 关闭创建目标模态框
 */
function closeCreateGoalModal() {
    document.getElementById('createGoalModal').classList.add('hidden');
}

/**
 * 启动轮询
 */
function startPolling() {
    if (state.refreshTimer) {
        clearInterval(state.refreshTimer);
    }
    state.refreshTimer = setInterval(refreshData, CONFIG.refreshInterval);
}

/**
 * 停止轮询
 */
function stopPolling() {
    if (state.refreshTimer) {
        clearInterval(state.refreshTimer);
        state.refreshTimer = null;
    }
}

// ==================== 工具函数 ====================

/**
 * 转义 HTML
 */
function escapeHtml(text) {
    const div = document.createElement('div');
    div.textContent = text;
    return div.innerHTML;
}

/**
 * 显示提示消息
 */
function showToast(message, type = 'info') {
    const bgColors = {
        success: 'bg-green-500',
        error: 'bg-red-500',
        info: 'bg-blue-500',
        warning: 'bg-yellow-500',
    };

    const toast = document.getElementById('toast');
    toast.className = `fixed top-4 right-4 px-4 py-3 rounded-lg shadow-lg z-50 transition-all duration-300 ${bgColors[type]} text-white`;
    toast.textContent = message;
    toast.classList.remove('hidden');

    setTimeout(() => {
        toast.classList.add('hidden');
    }, 3000);
}

// ==================== 启动应用 ====================
document.addEventListener('DOMContentLoaded', init);
