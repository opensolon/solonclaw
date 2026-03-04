/**
 * 对话页面 JavaScript
 */

const API_BASE = 'http://localhost:12345/api';
let sessionId = null;
let sessions = [];

// ==================== Markdown 渲染配置 ====================

// 注册 Highlight.js 语言
if (typeof hljs !== 'undefined') {
    hljs.registerLanguage('javascript', hljs.languages.javascript);
    hljs.registerLanguage('python', hljs.languages.python);
    hljs.registerLanguage('java', hljs.languages.java);
    hljs.registerLanguage('bash', hljs.languages.bash);
    hljs.registerLanguage('json', hljs.languages.json);
    hljs.registerLanguage('shell', hljs.languages.bash);
    hljs.registerLanguage('typescript', hljs.languages.javascript);
}

// 配置 marked
if (typeof marked !== 'undefined') {
    const renderer = new marked.Renderer();

    // 自定义代码块渲染（带语法高亮和复制按钮）
    renderer.code = function(code, language) {
        const validLang = language && hljs && hljs.getLanguage(language);
        let highlighted = code;

        if (validLang) {
            try {
                highlighted = hljs.highlight(code, { language: language || 'text' }).value;
            } catch (e) {
                console.warn('代码高亮失败:', e);
            }
        }

        const langDisplay = language || 'text';

        return `
            <div class="code-block-wrapper relative group">
                <div class="code-header flex items-center justify-between bg-gray-800 text-gray-300 px-4 py-2 text-xs rounded-t-md">
                    <span class="language-badge font-mono">${langDisplay}</span>
                    <button class="copy-code-btn hover:text-white transition-colors" onclick="copyCode(this)" title="复制代码">
                        <svg class="w-4 h-4" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                            <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M8 16H6a2 2 0 01-2-2V6a2 2 0 012-2h8a2 2 0 012 2v2m-6 12h8a2 2 0 002-2v-8a2 2 0 00-2-2h-8a2 2 0 00-2 2v8a2 2 0 002 2z"></path>
                        </svg>
                        <span class="ml-1">复制</span>
                    </button>
                </div>
                <pre class="bg-gray-900 rounded-b-md overflow-x-auto"><code class="hljs language-${langDisplay.toLowerCase()}">${highlighted}</code></pre>
            </div>
        `;
    };

    // 自定义表格渲染（带滚动）
    renderer.table = function(header, body) {
        return `
            <div class="table-wrapper overflow-x-auto my-4">
                <table class="min-w-full border-collapse">
                    <thead>
                        <tr class="bg-gray-50">${header}</tr>
                    </thead>
                    <tbody>${body}</tbody>
                </table>
            </div>
        `;
    };

    // 数学公式支持（KaTeX）
    renderer.html = function(html) {
        // 处理行内公式 $...$
        html = html.replace(/\$([^\n$]+?)\$/g, (match, formula) => {
            try {
                return katex.renderToString(formula.trim(), {
                    displayMode: false,
                    throwOnError: false
                });
            } catch (e) {
                return match;
            }
        });

        // 处理块级公式 $$...$$
        html = html.replace(/\$\$([\s\S]+?)\$\$/g, (match, formula) => {
            try {
                return katex.renderToString(formula.trim(), {
                    displayMode: true,
                    throwOnError: false
                });
            } catch (e) {
                return match;
            }
        });

        return html;
    };

    marked.setOptions({
        renderer: renderer,
        gfm: true,
        breaks: true,
        headerIds: true,
        mangle: false
    });
}

/**
 * 复制代码
 */
function copyCode(button) {
    const codeBlock = button.closest('.code-block-wrapper').querySelector('code');
    const code = codeBlock.textContent;

    navigator.clipboard.writeText(code).then(() => {
        const originalText = button.innerHTML;
        button.innerHTML = `
            <svg class="w-4 h-4 text-green-400" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M5 13l4 4L19 7"></path>
            </svg>
            <span class="ml-1 text-green-400">已复制</span>
        `;
        setTimeout(() => {
            button.innerHTML = originalText;
        }, 2000);
    }).catch(err => {
        console.error('复制失败:', err);
        showToast('复制失败', 'error');
    });
}

/**
 * 初始化
 */
function init() {
    const sendBtn = document.getElementById('sendButton');
    const clearBtn = document.getElementById('clearButton');
    const newSessionBtn = document.getElementById('newSessionButton');
    const messageInput = document.getElementById('messageInput');

    sendBtn.addEventListener('click', sendMessage);
    clearBtn.addEventListener('click', clearChat);
    newSessionBtn.addEventListener('click', createNewSession);

    messageInput.addEventListener('keydown', (e) => {
        if (e.key === 'Enter' && !e.shiftKey) {
            e.preventDefault();
            sendMessage();
        }
    });

    messageInput.addEventListener('input', updateCharCount);

    // 加载会话列表
    loadSessions();
}

/**
 * 加载会话列表
 */
async function loadSessions() {
    try {
        const data = await RequestUtil.get('/sessions', { showError: false });
        sessions = data.sessions || [];
        renderSessionList();
    } catch (e) {
        console.error('加载会话列表失败', e);
        renderSessionList();
    }
}

/**
 * 渲染会话列表
 */
function renderSessionList() {
    const container = document.getElementById('sessionList');

    if (sessions.length === 0) {
        container.innerHTML = `
            <div class="text-center text-gray-400 py-8 text-sm">
                暂无历史会话
            </div>
        `;
        return;
    }

    container.innerHTML = sessions.map(session => {
        const isActive = session.id === sessionId;
        const title = session.title || '新对话';
        const time = formatTime(session.updatedAt || session.createdAt);

        return `
            <div class="session-item ${isActive ? 'active' : ''} rounded-lg px-3 py-2.5 cursor-pointer mb-1 group"
                 onclick="switchSession('${session.id}')">
                <div class="flex items-center justify-between">
                    <div class="flex-1 min-w-0">
                        <div class="text-sm font-medium text-gray-800 truncate">${escapeHtml(title)}</div>
                        <div class="text-xs text-gray-500 mt-0.5">${time}</div>
                    </div>
                    <button
                        class="opacity-0 group-hover:opacity-100 p-1 hover:bg-gray-200 rounded transition-all duration-200"
                        onclick="event.stopPropagation(); deleteSession('${session.id}')"
                        title="删除会话"
                    >
                        <svg class="w-4 h-4 text-gray-500" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                            <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M19 7l-.867 12.142A2 2 0 0116.138 21H7.862a2 2 0 01-1.995-1.858L5 7m5 4v6m4-6v6m1-10V4a1 1 0 00-1-1h-4a1 1 0 00-1 1v3M4 7h16"></path>
                        </svg>
                    </button>
                </div>
            </div>
        `;
    }).join('');
}

/**
 * 格式化时间
 */
function formatTime(timestamp) {
    if (!timestamp) return '';

    const date = new Date(timestamp);
    const now = new Date();
    const diff = now - date;

    // 小于1分钟
    if (diff < 60000) {
        return '刚刚';
    }

    // 小于1小时
    if (diff < 3600000) {
        return Math.floor(diff / 60000) + '分钟前';
    }

    // 今天
    if (date.toDateString() === now.toDateString()) {
        return date.toLocaleTimeString('zh-CN', { hour: '2-digit', minute: '2-digit' });
    }

    // 昨天
    const yesterday = new Date(now);
    yesterday.setDate(yesterday.getDate() - 1);
    if (date.toDateString() === yesterday.toDateString()) {
        return '昨天 ' + date.toLocaleTimeString('zh-CN', { hour: '2-digit', minute: '2-digit' });
    }

    // 更早
    return date.toLocaleDateString('zh-CN', { month: 'short', day: 'numeric' });
}

/**
 * 创建新会话
 */
async function createNewSession() {
    sessionId = null;

    // 清空聊天容器，显示欢迎消息
    const container = document.getElementById('chatContainer');
    container.innerHTML = `
        <div class="flex justify-start">
            <div class="bg-gray-100 text-gray-800 rounded-2xl rounded-bl-sm px-4 py-3 max-w-[80%] markdown-content">
                <div class="font-semibold text-gray-800 mb-2">欢迎使用 SolonClaw 👋</div>
                <div class="text-gray-600 text-sm">我是您的智能助手，可以帮助您执行各种任务。请在下方输入您的问题或需求。</div>
            </div>
        </div>
    `;

    // 重新渲染会话列表（取消高亮）
    renderSessionList();

    showToast('已创建新会话', 'success');
}

/**
 * 切换会话
 */
async function switchSession(sessionId) {
    showLoading(true);

    try {
        const response = await RequestUtil.get(`/sessions/${sessionId}`, { showError: false });
        this.sessionId = sessionId;

        // 渲染会话消息
        const container = document.getElementById('chatContainer');
        const messages = response.messages || [];

        if (messages.length === 0) {
            container.innerHTML = `
                <div class="flex justify-start">
                    <div class="bg-gray-100 text-gray-800 rounded-2xl rounded-bl-sm px-4 py-3 max-w-[80%] markdown-content">
                        <div class="font-semibold text-gray-800 mb-2">欢迎使用 SolonClaw 👋</div>
                        <div class="text-gray-600 text-sm">我是您的智能助手，可以帮助您执行各种任务。请在下方输入您的问题或需求。</div>
                    </div>
                </div>
            `;
        } else {
            container.innerHTML = messages.map(msg => {
                const role = msg.role === 'user' ? 'flex justify-end' : 'flex justify-start';
                const bubbleClass = msg.role === 'user'
                    ? 'bg-blue-600 text-white rounded-2xl rounded-br-sm px-4 py-2 max-w-[80%]'
                    : 'bg-gray-100 text-gray-800 rounded-2xl rounded-bl-sm px-4 py-2 max-w-[80%] markdown-content';

                return `
                    <div class="message-fade-in ${role}">
                        <div class="${bubbleClass}">${escapeHtml(msg.content)}</div>
                    </div>
                `;
            }).join('');
        }

        // 滚动到底部
        container.scrollTop = container.scrollHeight;

        // 重新渲染会话列表（更新高亮）
        renderSessionList();

    } catch (error) {
        showToast('加载会话失败: ' + error.message, 'error');
    } finally {
        showLoading(false);
    }
}

/**
 * 删除会话
 */
async function deleteSession(sessionId) {
    if (!confirm('确定要删除这个会话吗？')) {
        return;
    }

    showLoading(true);

    try {
        await RequestUtil.delete(`/sessions/${sessionId}`);

        // 如果删除的是当前会话，清空当前会话ID
        if (sessionId === this.sessionId) {
            this.sessionId = null;
            const container = document.getElementById('chatContainer');
            container.innerHTML = `
                <div class="flex justify-start">
                    <div class="bg-gray-100 text-gray-800 rounded-2xl rounded-bl-sm px-4 py-3 max-w-[80%] markdown-content">
                        <div class="font-semibold text-gray-800 mb-2">欢迎使用 SolonClaw 👋</div>
                        <div class="text-gray-600 text-sm">我是您的智能助手，可以帮助您执行各种任务。请在下方输入您的问题或需求。</div>
                    </div>
                </div>
            `;
        }

        // 重新加载会话列表
        await loadSessions();

        showToast('会话已删除', 'success');

    } catch (error) {
        showToast('删除失败: ' + error.message, 'error');
    } finally {
        showLoading(false);
    }
}

/**
 * 发送消息
 */
async function sendMessage() {
    const input = document.getElementById('messageInput');
    const message = input.value.trim();

    if (!message) return;

    // 显示用户消息
    appendMessage('user', message);
    input.value = '';
    updateCharCount();

    // 创建AI消息气泡，显示"思考中"
    const aiBubble = appendThinkingMessage();

    try {
        const response = await RequestUtil.post('/chat', {
            message: message,
            sessionId: sessionId
        });

        if (response.sessionId) {
            sessionId = response.sessionId;
            // 重新加载会话列表以更新
            loadSessions();
        }

        // 替换"思考中"为实际回复（Markdown渲染）
        replaceThinkingMessage(aiBubble, response.response);

    } catch (error) {
        replaceThinkingMessage(aiBubble, '❌ 发送失败: ' + error.message);
        showToast('发送失败: ' + error.message, 'error');
    }
}

/**
 * 添加"思考中"消息气泡
 */
function appendThinkingMessage() {
    const container = document.getElementById('chatContainer');

    const messageDiv = document.createElement('div');
    messageDiv.className = 'message-fade-in flex justify-start';
    messageDiv.id = 'thinking-' + Date.now();

    const bubble = document.createElement('div');
    bubble.className = 'bg-gray-100 text-gray-800 rounded-2xl rounded-bl-sm px-4 py-3 max-w-[80%] markdown-content flex items-center space-x-2';

    bubble.innerHTML = `
        <span class="text-gray-500">思考中</span>
        <div class="flex space-x-1">
            <span class="w-2 h-2 bg-gray-400 rounded-full animate-bounce" style="animation-delay: 0ms;"></span>
            <span class="w-2 h-2 bg-gray-400 rounded-full animate-bounce" style="animation-delay: 150ms;"></span>
            <span class="w-2 h-2 bg-gray-400 rounded-full animate-bounce" style="animation-delay: 300ms;"></span>
        </div>
    `;

    messageDiv.appendChild(bubble);
    container.appendChild(messageDiv);

    // 滚动到底部
    container.scrollTop = container.scrollHeight;

    return { messageDiv, bubble };
}

/**
 * 替换"思考中"为实际回复
 */
function replaceThinkingMessage(aiMessage, content) {
    const { messageDiv, bubble } = aiMessage;

    // 移除 flex 布局相关的类（这些类用于"思考中"动画）
    bubble.classList.remove('flex', 'items-center', 'space-x-2');

    // 清空思考中动画
    bubble.innerHTML = '';

    // 将 Markdown 转换为 HTML
    const html = marked.parse(content);

    // 使用智能打字机效果（保留HTML标签结构）
    typeWriterHTML(bubble, html);
}

/**
 * 智能打字机效果（保留HTML标签）
 */
function typeWriterHTML(element, html, index = 0) {
    if (index >= html.length) {
        // 打字完成，触发动画
        element.classList.add('typing-complete');
        return;
    }

    let nextIndex = index + 1;

    // 检查是否在HTML标签中
    if (html[index] === '<') {
        // 找到标签结束位置
        const closeIndex = html.indexOf('>', index);
        if (closeIndex !== -1) {
            // 一次性输出整个标签
            element.innerHTML = html.substring(0, closeIndex + 1);
            nextIndex = closeIndex + 1;

            // 标签立即显示，不延迟
            setTimeout(() => typeWriterHTML(element, html, nextIndex), 5);
            return;
        }
    }

    // 检查是否在HTML实体中（如 &nbsp;）
    if (html[index] === '&') {
        const semiIndex = html.indexOf(';', index);
        if (semiIndex !== -1 && semiIndex - index < 10) {
            // 一次性输出整个实体
            element.innerHTML = html.substring(0, semiIndex + 1);
            nextIndex = semiIndex + 1;

            setTimeout(() => typeWriterHTML(element, html, nextIndex), 5);
            return;
        }
    }

    // 普通字符，逐个输出
    element.innerHTML = html.substring(0, nextIndex);

    // 滚动到底部
    const container = document.getElementById('chatContainer');
    container.scrollTop = container.scrollHeight;

    // 继续下一个字符
    setTimeout(() => typeWriterHTML(element, html, nextIndex), 15);
}

/**
 * 清空对话
 */
async function clearChat() {
    if (!sessionId) {
        showToast('没有可清空的对话', 'warning');
        return;
    }

    if (!confirm('确定要清空对话历史吗？')) {
        return;
    }

    showLoading(true);

    try {
        await RequestUtil.delete(`/sessions/${sessionId}`);
        sessionId = null;

        // 清空聊天容器
        const container = document.getElementById('chatContainer');
        container.innerHTML = `
            <div class="flex justify-start">
                <div class="bg-gray-100 text-gray-800 rounded-2xl rounded-bl-sm px-4 py-3 max-w-[80%] markdown-content">
                    <div class="font-semibold text-gray-800 mb-2">欢迎使用 SolonClaw 👋</div>
                    <div class="text-gray-600 text-sm">我是您的智能助手，可以帮助您执行各种任务。请在下方输入您的问题或需求。</div>
                </div>
            </div>
        `;

        // 重新加载会话列表
        await loadSessions();

        showToast('对话已清空', 'success');

    } catch (error) {
        showToast('清空失败: ' + error.message, 'error');
    } finally {
        showLoading(false);
    }
}

/**
 * 添加消息到界面（用户消息）
 */
function appendMessage(role, content) {
    const container = document.getElementById('chatContainer');

    const messageDiv = document.createElement('div');
    messageDiv.className = `message-fade-in ${role === 'user' ? 'flex justify-end' : 'flex justify-start'}`;

    const bubble = document.createElement('div');
    bubble.className = role === 'user'
        ? 'bg-blue-600 text-white rounded-2xl rounded-br-sm px-4 py-2 max-w-[80%]'
        : 'bg-gray-100 text-gray-800 rounded-2xl rounded-bl-sm px-4 py-2 max-w-[80%] markdown-content';

    bubble.textContent = content;
    messageDiv.appendChild(bubble);
    container.appendChild(messageDiv);

    // 滚动到底部
    container.scrollTop = container.scrollHeight;
}

/**
 * 更新字符计数
 */
function updateCharCount() {
    const input = document.getElementById('messageInput');
    const count = document.getElementById('charCount');
    const length = input.value.length;
    count.textContent = `${length} / 2000`;

    if (length > 2000) {
        count.classList.add('text-red-500');
    } else {
        count.classList.remove('text-red-500');
    }
}

/**
 * 显示加载状态
 */
function showLoading(show) {
    const overlay = document.getElementById('loadingOverlay');
    if (show) {
        overlay.classList.remove('hidden');
    } else {
        overlay.classList.add('hidden');
    }
}

/**
 * 显示提示消息
 */
function showToast(message, type = 'info') {
    const toast = document.getElementById('toast');
    toast.textContent = message;
    toast.className = 'fixed top-4 right-4 px-4 py-3 rounded-lg shadow-lg z-50 transition-all duration-300';

    switch (type) {
        case 'success':
            toast.classList.add('bg-green-500', 'text-white');
            break;
        case 'error':
            toast.classList.add('bg-red-500', 'text-white');
            break;
        case 'warning':
            toast.classList.add('bg-yellow-500', 'text-white');
            break;
        default:
            toast.classList.add('bg-blue-500', 'text-white');
    }

    toast.classList.remove('hidden');

    setTimeout(() => {
        toast.classList.add('hidden');
    }, 3000);
}

/**
 * HTML 转义
 */
function escapeHtml(text) {
    const div = document.createElement('div');
    div.textContent = text;
    return div.innerHTML;
}

// 页面加载完成后初始化
document.addEventListener('DOMContentLoaded', init);
