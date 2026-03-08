const API_BASE = 'http://localhost:12345/api/autonomous';
let currentTaskTab = 'pending';

const TASK_TYPE_LABELS = {
    'SELF_CHECK': '系统自检',
    'GOAL_CHECK': '目标检查',
    'KNOWLEDGE_UPDATE': '知识更新',
    'SKILL_INSTALL': '技能安装',
    'REFLECTION': '自我反思',
    'FOLLOW_UP': '后续跟进',
    'CUSTOM': '自定义任务'
};

async function init() {
    await loadData();
    setInterval(loadData, 10000);
}

async function loadData() {
    await Promise.all([loadStats(), loadTasks(currentTaskTab)]);
}

async function loadStats() {
    try {
        const stats = await RequestUtil.get(`${API_BASE}/stats`, { showError: false });
        document.getElementById('statTotalTasks').textContent = stats.totalTasksCreated || 0;
        document.getElementById('statCompletedTasks').textContent = stats.totalTasksCompleted || 0;
    } catch (e) {
        console.error('加载统计失败', e);
    }
}

async function loadTasks(tab) {
    currentTaskTab = tab;
    try {
        // 使用独立端点而不是统一的 /tasks?status= 端点
        const endpoint = `${API_BASE}/tasks/${tab}`;
        const data = await RequestUtil.get(endpoint, { showError: false });

        // 处理不同的响应格式
        const tasks = data.data || data.tasks || [];

        document.getElementById(`count-${tab}`).textContent = tasks.length;
        renderTasks(tasks);
    } catch (e) {
        document.getElementById('taskContent').innerHTML = `<div class="text-center text-gray-500 p-4">加载失败</div>`;
    }
}

function renderTasks(tasks) {
    const container = document.getElementById('taskContent');

    if (tasks.length === 0) {
        container.innerHTML = `<div class="text-center text-gray-500 p-4">暂无任务</div>`;
        return;
    }

    container.innerHTML = tasks.map(task => {
        const typeLabel = TASK_TYPE_LABELS[task.type] || task.type;
        return `
            <div class="bg-gray-50 rounded-lg p-3 mb-2 card-hover">
                <div class="flex justify-between items-start">
                    <div>
                        <div class="font-medium text-gray-800">${typeLabel}</div>
                        <div class="text-sm text-gray-600">${task.description || ''}</div>
                    </div>
                    <div class="text-xs text-gray-500">
                        <div>优先级: ${task.priority || 1}</div>
                        <div>状态: ${task.status || 'PENDING'}</div>
                    </div>
                </div>
            </div>
        `;
    }).join('');
}

function showTaskTab(tab) {
    document.querySelectorAll('.tab-btn').forEach(btn => {
        btn.classList.remove('border-blue-500', 'text-blue-600');
        btn.classList.add('border-transparent', 'text-gray-500');
    });
    document.getElementById(`tab-${tab}`).classList.add('border-blue-500', 'text-blue-600');
    document.getElementById(`tab-${tab}`).classList.remove('border-transparent', 'text-gray-500');
    loadTasks(tab);
}

document.addEventListener('DOMContentLoaded', init);
