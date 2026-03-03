/**
 * SolonClaw 主布局 JavaScript
 * 处理页面切换和侧边栏功能
 */

const API_BASE = 'http://localhost:12345/api';

let currentPage = 'chat';
let sidebarOpen = false;

/**
 * 初始化
 */
function init() {
    // 设置默认激活页面
    setActiveMenuItem('chat');

    // 绑定事件
    document.getElementById('menuToggle').addEventListener('click', toggleSidebar);
    document.getElementById('sidebarOverlay').addEventListener('click', closeSidebar);

    // 检查健康状态
    checkHealth();
    setInterval(checkHealth, 30000);
}

/**
 * 切换页面
 */
function switchPage(page) {
    const iframe = document.getElementById('contentFrame');
    const url = `pages/${page}.html`;

    iframe.src = url;
    currentPage = page;

    setActiveMenuItem(page);

    // 移动端关闭侧边栏
    if (window.innerWidth < 1024) {
        closeSidebar();
    }
}

/**
 * 设置激活菜单项
 */
function setActiveMenuItem(page) {
    document.querySelectorAll('.menu-item').forEach(item => {
        item.classList.remove('active');
        if (item.dataset.page === page) {
            item.classList.add('active');
        }
    });
}

/**
 * 切换侧边栏
 */
function toggleSidebar() {
    const sidebar = document.getElementById('sidebar');
    const overlay = document.getElementById('sidebarOverlay');

    sidebarOpen = !sidebarOpen;

    if (sidebarOpen) {
        sidebar.classList.remove('-translate-x-full');
        overlay.classList.remove('hidden');
    } else {
        sidebar.classList.add('-translate-x-full');
        overlay.classList.add('hidden');
    }
}

/**
 * 关闭侧边栏
 */
function closeSidebar() {
    const sidebar = document.getElementById('sidebar');
    const overlay = document.getElementById('sidebarOverlay');

    sidebarOpen = false;
    sidebar.classList.add('-translate-x-full');
    overlay.classList.add('hidden');
}

/**
 * 检查健康状态
 */
async function checkHealth() {
    try {
        const response = await fetch(`${API_BASE}/health`);
        const data = await response.json();

        const statusIndicator = document.getElementById('statusIndicator');

        if (data.code === 200) {
            statusIndicator.innerHTML = `
                <span class="status-dot online pulse"></span>
                <span class="text-sm font-medium text-gray-600">已连接</span>
            `;
        } else {
            statusIndicator.innerHTML = `
                <span class="status-dot offline"></span>
                <span class="text-sm font-medium text-gray-600">未连接</span>
            `;
        }
    } catch (error) {
        const statusIndicator = document.getElementById('statusIndicator');
        statusIndicator.innerHTML = `
            <span class="status-dot offline"></span>
            <span class="text-sm font-medium text-gray-600">未连接</span>
        `;
    }
}

// 页面加载完成后初始化
document.addEventListener('DOMContentLoaded', init);
