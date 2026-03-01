/**
 * SolonClaw 前端应用
 * 现代化聊天界面，支持 SSE 流式响应
 */

// ==================== 配置 ====================
const CONFIG = {
    apiBase: 'http://localhost:8080/api',
    maxChars: 2000,
    requestTimeout: 120000, // 2分钟超时
};

// ==================== 状态管理 ====================
const state = {
    sessionId: null,
    isLoading: false,
    messages: [],
    abortController: null,
    useStreaming: true, // 默认使用流式响应
};

// ==================== DOM 元素 ====================
const elements = {
    chatContainer: document.getElementById('chatContainer'),
    messageInput: document.getElementById('messageInput'),
    sendButton: document.getElementById('sendButton'),
    clearButton: document.getElementById('clearButton'),
    statusIndicator: document.getElementById('statusIndicator'),
    loadingOverlay: document.getElementById('loadingOverlay'),
    charCount: document.getElementById('charCount'),
    toast: document.getElementById('toast'),
};

// ==================== 初始化 ====================
function init() {
    // 生成或恢复 sessionId
    state.sessionId = localStorage.getItem('sessionId') || generateSessionId();
    localStorage.setItem('sessionId', state.sessionId);

    // 绑定事件
    bindEvents();

    // 加载历史记录
    loadHistory();

    // 检查服务状态
    checkHealth();

    console.log('SolonClaw 前端初始化完成，sessionId:', state.sessionId);
}

// ==================== 事件绑定 ====================
function bindEvents() {
    // 发送按钮点击
    elements.sendButton.addEventListener('click', sendMessage);

    // 清空按钮点击
    elements.clearButton.addEventListener('click', clearChat);

    // 输入框事件
    elements.messageInput.addEventListener('input', handleInput);
    elements.messageInput.addEventListener('keydown', handleKeydown);

    // 自动调整输入框高度
    elements.messageInput.addEventListener('input', autoResizeTextarea);

    // 监听聊天容器内的图片加载事件
    elements.chatContainer.addEventListener('load', (e) => {
        if (e.target.tagName === 'IMG') {
            // 图片加载完成后滚动到底部
            requestAnimationFrame(() => {
                scrollToBottom(true);
            });
        }
    }, true); // 使用捕获阶段

    // 使用 MutationObserver 监听 DOM 变化，检测新增的图片
    const observer = new MutationObserver((mutations) => {
        mutations.forEach((mutation) => {
            mutation.addedNodes.forEach((node) => {
                if (node.nodeType === 1) { // 元素节点
                    const images = node.querySelectorAll ? node.querySelectorAll('img') : [];
                    images.forEach(img => {
                        // 如果图片已经加载完成，立即滚动
                        if (img.complete) {
                            requestAnimationFrame(() => {
                                scrollToBottom(true);
                            });
                        } else {
                            // 否则等待图片加载
                            img.addEventListener('load', () => {
                                requestAnimationFrame(() => {
                                    scrollToBottom(true);
                                });
                            });
                        }
                    });
                }
            });
        });
    });

    observer.observe(elements.chatContainer, {
        childList: true,
        subtree: true
    });
}

// ==================== API 调用 ====================

/**
 * 检查服务健康状态
 */
async function checkHealth() {
    try {
        const response = await fetch(`${CONFIG.apiBase}/health`);
        const data = await response.json();
        if (data.code === 200) {
            updateStatus(true);
        } else {
            updateStatus(false);
        }
    } catch (error) {
        updateStatus(false);
        console.error('健康检查失败:', error);
    }
}

/**
 * 加载历史记录
 */
async function loadHistory() {
    try {
        const response = await fetch(`${CONFIG.apiBase}/sessions/${state.sessionId}`);
        const data = await response.json();

        if (data.code === 200 && data.data.history && data.data.history.length > 0) {
            state.messages = data.data.history;
            renderHistory();
            // 历史记录加载完成后滚动到底部
            requestAnimationFrame(() => {
                scrollToBottom();
            });
        }
    } catch (error) {
        console.error('加载历史失败:', error);
    }
}

/**
 * 发送消息
 */
async function sendMessage() {
    const message = elements.messageInput.value.trim();
    if (!message || state.isLoading) return;

    // 清空输入框
    elements.messageInput.value = '';
    handleInput();
    autoResizeTextarea();

    // 添加用户消息到界面
    addMessage('user', message);

    // 根据设置选择流式或普通请求
    if (state.useStreaming) {
        await sendMessageStreaming(message);
    } else {
        await sendMessageNormal(message);
    }
}

/**
 * 使用 SSE 流式响应发送消息
 */
async function sendMessageStreaming(message) {
    // 创建流式消息占位
    addStreamingMessage();
    setLoading(true);

    let fullContent = '';
    let hasError = false;

    try {
        state.abortController = new AbortController();

        const response = await fetch(`${CONFIG.apiBase}/chat/stream`, {
            method: 'POST',
            headers: {
                'Content-Type': 'application/json',
                'Accept': 'text/event-stream',
            },
            body: JSON.stringify({
                message: message,
                sessionId: state.sessionId,
            }),
            signal: state.abortController.signal,
        });

        if (!response.ok) {
            throw new Error(`HTTP error! status: ${response.status}`);
        }

        const reader = response.body.getReader();
        const decoder = new TextDecoder();
        let buffer = '';

        while (true) {
            const { done, value } = await reader.read();

            if (done) break;

            buffer += decoder.decode(value, { stream: true });
            const lines = buffer.split('\n');
            buffer = lines.pop() || ''; // 保留不完整的行

            for (const line of lines) {
                if (line.startsWith('event:')) {
                    // 处理事件名称行
                    const eventName = line.substring(7).trim();
                    if (eventName === 'session') {
                        // 下一个 data 行会包含 sessionId
                    }
                } else if (line.startsWith('data:')) {
                    const data = line.substring(5).trim();
                    if (data) {
                        try {
                            const event = JSON.parse(data);
                            // 处理流式事件，累积增量内容
                            handleStreamEvent(event, (incrementalContent) => {
                                fullContent += incrementalContent;
                                updateStreamingMessage(fullContent);
                            });
                        } catch (e) {
                            console.warn('解析 SSE 数据失败:', data);
                        }
                    }
                }
            }
        }

        // 完成流式消息
        finishStreamingMessage();

    } catch (error) {
        if (error.name === 'AbortError') {
            showToast('请求已取消', 'warning');
            finishStreamingMessage();
            if (fullContent) {
                // 保留已收到的内容
            } else {
                removeStreamingMessage();
            }
        } else {
            console.error('流式请求失败:', error);
            finishStreamingMessage();
            removeStreamingMessage();
            addMessage('error', '发送失败: ' + error.message);
            hasError = true;
        }
    } finally {
        setLoading(false);
        state.abortController = null;
    }
}

/**
 * 处理流式事件
 * @param {Object} event - 流式事件对象
 * @param {Function} onContent - 内容回调，接收增量内容
 */
function handleStreamEvent(event, onContent) {
    console.log('收到流式事件:', event);

    switch (event.type) {
        case 'START':
            // 开始处理
            break;

        case 'CONTENT':
            // 内容片段 - 后端发送的是增量内容，需要累积
            if (event.content) {
                onContent(event.content);
            }
            break;

        case 'TOOL_CALL':
            // 工具调用开始
            showToolCall(event.content || '执行工具...');
            break;

        case 'TOOL_DONE':
            // 工具调用完成
            hideToolCall();
            break;

        case 'END':
            // 处理完成
            break;

        case 'ERROR':
            // 错误
            showToast(event.content || '处理出错', 'error');
            break;

        case 'done':
            // 流结束标记
            break;
    }
}

/**
 * 使用普通 HTTP 请求发送消息
 */
async function sendMessageNormal(message) {
    setLoading(true);

    try {
        state.abortController = new AbortController();
        const timeoutId = setTimeout(() => state.abortController.abort(), CONFIG.requestTimeout);

        const response = await fetch(`${CONFIG.apiBase}/chat`, {
            method: 'POST',
            headers: {
                'Content-Type': 'application/json',
            },
            body: JSON.stringify({
                message: message,
                sessionId: state.sessionId,
            }),
            signal: state.abortController.signal,
        });

        clearTimeout(timeoutId);
        const data = await response.json();

        if (data.code === 200) {
            // 更新 sessionId（如果是新会话）
            if (data.data.sessionId) {
                state.sessionId = data.data.sessionId;
                localStorage.setItem('sessionId', state.sessionId);
            }
            // 添加 AI 响应
            addMessage('assistant', data.data.response);
        } else {
            showToast(data.message || '请求失败', 'error');
            addMessage('error', data.message || '请求失败');
        }
    } catch (error) {
        if (error.name === 'AbortError') {
            showToast('请求超时', 'error');
            addMessage('error', '请求超时，请重试');
        } else {
            console.error('发送消息失败:', error);
            showToast('发送失败: ' + error.message, 'error');
            addMessage('error', '发送失败: ' + error.message);
        }
    } finally {
        setLoading(false);
        state.abortController = null;
    }
}

/**
 * 清空对话
 */
async function clearChat() {
    if (!confirm('确定要清空所有对话记录吗？')) return;

    try {
        const response = await fetch(`${CONFIG.apiBase}/sessions/${state.sessionId}`, {
            method: 'DELETE',
        });
        const data = await response.json();

        if (data.code === 200) {
            // 重置状态
            state.sessionId = generateSessionId();
            localStorage.setItem('sessionId', state.sessionId);
            state.messages = [];

            // 清空界面
            elements.chatContainer.innerHTML = `
                <div class="flex justify-center">
                    <div class="bg-gradient-to-r from-blue-50 to-indigo-50 rounded-2xl p-6 max-w-md text-center">
                        <div class="w-16 h-16 bg-blue-100 rounded-full flex items-center justify-center mx-auto mb-4">
                            <svg class="w-8 h-8 text-blue-600" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                                <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M8 10h.01M12 10h.01M16 10h.01M9 16H5a2 2 0 01-2-2V6a2 2 0 012-2h14a2 2 0 012 2v8a2 2 0 01-2 2h-5l-5 5v-5z"></path>
                            </svg>
                        </div>
                        <h2 class="text-lg font-semibold text-gray-800 mb-2">对话已清空</h2>
                        <p class="text-gray-600 text-sm">开始新的对话吧！</p>
                    </div>
                </div>
            `;

            showToast('对话已清空', 'success');
        } else {
            showToast(data.message || '清空失败', 'error');
        }
    } catch (error) {
        console.error('清空对话失败:', error);
        showToast('清空失败: ' + error.message, 'error');
    }
}

// ==================== UI 渲染 ====================

/**
 * 添加消息到界面
 */
function addMessage(role, content) {
    const messageDiv = document.createElement('div');
    messageDiv.className = `flex ${role === 'user' ? 'justify-end' : 'justify-start'} message-fade-in`;

    if (role === 'user') {
        messageDiv.innerHTML = `
            <div class="flex items-end space-x-2 max-w-[80%]">
                <button class="resend-btn bg-blue-500 hover:bg-blue-700 text-white rounded-xl px-3 py-2 shadow-md transition-all duration-200 flex items-center space-x-1"
                        onclick="resendMessage(this)" data-message="${escapeHtml(content).replace(/"/g, '&quot;')}" title="重新输入">
                    <svg class="w-4 h-4" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                        <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M4 4v5h.582m15.356 2A8.001 8.001 0 004.582 15.356m0 0A8.003 8.003 0 013.825 0m0 0a8.003 8.003 0 01-4.582 4.581m0 0A8.003 8.003 0 00-3.825 0M5 12a8 8 0 1116 8 8 8 0 01-16 0z"></path>
                    </svg>
                </button>
                <div class="max-w-full bg-blue-600 text-white rounded-2xl rounded-br-md px-4 py-3 shadow-md">
                    <p class="whitespace-pre-wrap">${escapeHtml(content)}</p>
                </div>
            </div>
        `;
    } else if (role === 'assistant') {
        messageDiv.innerHTML = `
            <div class="flex items-start space-x-3 max-w-[80%]">
                <div class="w-8 h-8 bg-gradient-to-br from-blue-500 to-indigo-500 rounded-lg flex items-center justify-center flex-shrink-0">
                    <svg class="w-5 h-5 text-white" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                        <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M9.75 17L9 20l-1 1h8l-1-1-.75-3M3 13h18M5 17h14a2 2 0 002-2V5a2 2 0 00-2-2H5a2 2 0 00-2 2v10a2 2 0 002 2z"></path>
                    </svg>
                </div>
                <div class="bg-gray-100 rounded-2xl rounded-tl-md px-4 py-3 shadow-md">
                    <div class="markdown-content text-gray-800">${renderMarkdown(content)}</div>
                </div>
            </div>
        `;
    } else if (role === 'error') {
        messageDiv.innerHTML = `
            <div class="flex items-start space-x-3 max-w-[80%]">
                <div class="w-8 h-8 bg-red-500 rounded-lg flex items-center justify-center flex-shrink-0">
                    <svg class="w-5 h-5 text-white" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                        <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M12 8v4m0 4h.01M21 12a9 9 0 11-18 0 9 9 0 0118 0z"></path>
                    </svg>
                </div>
                <div class="bg-red-50 border border-red-200 rounded-2xl rounded-tl-md px-4 py-3">
                    <p class="text-red-600">${escapeHtml(content)}</p>
                </div>
            </div>
        `;
    }

    // 移除欢迎消息（如果存在）
    const welcomeMsg = elements.chatContainer.querySelector('.bg-gradient-to-r.from-blue-50');
    if (welcomeMsg) {
        welcomeMsg.parentElement.remove();
    }

    elements.chatContainer.appendChild(messageDiv);

    // 添加消息后滚动到底部（使用平滑滚动）
    requestAnimationFrame(() => {
        scrollToBottom(false); // 立即滚动，不使用平滑效果
    });
}

/**
 * 渲染历史记录
 */
function renderHistory() {
    // 清空现有消息
    elements.chatContainer.innerHTML = '';

    // 渲染每条消息
    state.messages.forEach(msg => {
        const role = msg.role === 'user' ? 'user' : 'assistant';
        addMessage(role, msg.content);
    });
}

/**
 * 显示流式响应占位消息
 */
function addStreamingMessage() {
    const messageDiv = document.createElement('div');
    messageDiv.className = 'flex justify-start message-fade-in';
    messageDiv.id = 'streaming-message';
    messageDiv.innerHTML = `
        <div class="flex items-start space-x-3 max-w-[80%]">
            <div class="w-8 h-8 bg-gradient-to-br from-blue-500 to-indigo-500 rounded-lg flex items-center justify-center flex-shrink-0">
                <svg class="w-5 h-5 text-white" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                    <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M9.75 17L9 20l-1 1h8l-1-1-.75-3M3 13h18M5 17h14a2 2 0 002-2V5a2 2 0 00-2-2H5a2 2 0 00-2 2v10a2 2 0 002 2z"></path>
                </svg>
            </div>
            <div class="bg-gray-100 rounded-2xl rounded-tl-md px-4 py-3 shadow-md">
                <div class="markdown-content text-gray-800 typing-cursor" id="streaming-content">
                    <span class="text-gray-400">思考中...</span>
                </div>
            </div>
        </div>
    `;

    // 移除欢迎消息
    const welcomeMsg = elements.chatContainer.querySelector('.bg-gradient-to-r.from-blue-50');
    if (welcomeMsg) {
        welcomeMsg.parentElement.remove();
    }

    elements.chatContainer.appendChild(messageDiv);

    // 流式消息添加后滚动到底部
    requestAnimationFrame(() => {
        scrollToBottom(false);
    });
}

/**
 * 更新流式消息内容
 */
function updateStreamingMessage(content) {
    const contentEl = document.getElementById('streaming-content');
    if (contentEl) {
        contentEl.innerHTML = renderMarkdown(content);
        contentEl.classList.remove('text-gray-400');
        // 流式更新时滚动到底部
        requestAnimationFrame(() => {
            scrollToBottom(true); // 使用平滑滚动
        });
    }
}

/**
 * 完成流式消息
 */
function finishStreamingMessage() {
    const contentEl = document.getElementById('streaming-content');
    if (contentEl) {
        contentEl.classList.remove('typing-cursor');
        contentEl.removeAttribute('id');
    }

    // 移除流式消息容器 ID
    const messageEl = document.getElementById('streaming-message');
    if (messageEl) {
        messageEl.removeAttribute('id');
    }

    // 完成后等待图片加载并滚动到底部
    scrollToBottomAfterImages();
}

/**
 * 移除流式消息
 */
function removeStreamingMessage() {
    const messageEl = document.getElementById('streaming-message');
    if (messageEl) {
        messageEl.remove();
    }
}

/**
 * 显示工具调用提示
 */
function showToolCall(toolName) {
    let toolIndicator = document.getElementById('tool-indicator');
    if (!toolIndicator) {
        toolIndicator = document.createElement('div');
        toolIndicator.id = 'tool-indicator';
        toolIndicator.className = 'fixed bottom-24 left-1/2 transform -translate-x-1/2 bg-blue-600 text-white px-4 py-2 rounded-full shadow-lg flex items-center space-x-2';
        document.body.appendChild(toolIndicator);
    }

    toolIndicator.innerHTML = `
        <div class="w-4 h-4 border-2 border-white border-t-transparent rounded-full animate-spin"></div>
        <span>${escapeHtml(toolName)}</span>
    `;
    toolIndicator.classList.remove('hidden');
}

/**
 * 隐藏工具调用提示
 */
function hideToolCall() {
    const toolIndicator = document.getElementById('tool-indicator');
    if (toolIndicator) {
        toolIndicator.classList.add('hidden');
    }
}

// ==================== 工具函数 ====================

/**
 * 重新发送消息（将消息内容重新放入输入框）
 */
function resendMessage(button) {
    const message = button.getAttribute('data-message');
    if (message) {
        elements.messageInput.value = message;
        handleInput();
        autoResizeTextarea();
        elements.messageInput.focus();
        showToast('消息已放入输入框', 'info');
    }
}

/**
 * 生成会话 ID
 */
function generateSessionId() {
    return 'sess-' + Date.now() + '-' + Math.random().toString(36).substr(2, 9);
}

/**
 * 转义 HTML
 */
function escapeHtml(text) {
    const div = document.createElement('div');
    div.textContent = text;
    return div.innerHTML;
}

/**
 * 简单的 Markdown 渲染
 */
function renderMarkdown(text) {
    if (!text) return '';

    // 转义 HTML
    let html = escapeHtml(text);

    // 图片（需要在代码块之前处理，避免被转义）
    html = html.replace(/!\[([^\]]*)\]\(([^)]+)\)/g, (match, alt, src) => {
        // 检查是否为临时文件访问链接
        if (src.startsWith('/api/file?token=')) {
            return `<img src="${src}" alt="${alt}" class="markdown-image" style="max-width: 100%; height: auto; border-radius: 8px; box-shadow: 0 2px 8px rgba(0,0,0,0.1); margin: 8px 0;" />`;
        }
        // 判断是否为 Base64 图片
        else if (src.startsWith('data:image')) {
            return `<img src="${src}" alt="${alt}" class="markdown-image" style="max-width: 100%; height: auto; border-radius: 8px; box-shadow: 0 2px 8px rgba(0,0,0,0.1); margin: 8px 0;" />`;
        }
        // 其他 URL（可能是相对路径）
        else {
            return `<img src="${src}" alt="${alt}" class="markdown-image" style="max-width: 100%; height: auto; border-radius: 8px; box-shadow: 0 2px 8px rgba(0,0,0,0.1); margin: 8px 0;" />`;
        }
    });

    // 代码块
    html = html.replace(/```(\w*)\n([\s\S]*?)```/g, '<pre><code class="language-$1">$2</code></pre>');

    // 行内代码
    html = html.replace(/`([^`]+)`/g, '<code>$1</code>');

    // 标题
    html = html.replace(/^### (.+)$/gm, '<h3>$1</h3>');
    html = html.replace(/^## (.+)$/gm, '<h2>$1</h2>');
    html = html.replace(/^# (.+)$/gm, '<h1>$1</h1>');

    // 粗体和斜体
    html = html.replace(/\*\*(.+?)\*\*/g, '<strong>$1</strong>');
    html = html.replace(/\*(.+?)\*/g, '<em>$1</em>');

    // 列表
    html = html.replace(/^- (.+)$/gm, '<li>$1</li>');
    html = html.replace(/(<li>.*<\/li>)/s, '<ul>$1</ul>');

    // 引用
    html = html.replace(/^&gt; (.+)$/gm, '<blockquote>$1</blockquote>');

    // 换行
    html = html.replace(/\n/g, '<br>');

    return html;
}

/**
 * 滚动到底部
 */
/**
 * 滚动到底部
 * 使用平滑滚动，支持图片加载后自动滚动
 */
function scrollToBottom(smooth = true) {
    if (!elements.chatContainer) return;

    if (smooth) {
        elements.chatContainer.scrollTo({
            top: elements.chatContainer.scrollHeight,
            behavior: 'smooth'
        });
    } else {
        elements.chatContainer.scrollTop = elements.chatContainer.scrollHeight;
    }
}

/**
 * 等待图片加载后滚动到底部
 */
function scrollToBottomAfterImages() {
    // 等待所有图片加载完成
    const images = elements.chatContainer.querySelectorAll('img');
    if (images.length === 0) {
        scrollToBottom();
        return;
    }

    let loadedCount = 0;
    const totalImages = images.length;

    images.forEach(img => {
        if (img.complete) {
            loadedCount++;
        } else {
            img.addEventListener('load', () => {
                loadedCount++;
                if (loadedCount === totalImages) {
                    scrollToBottom();
                }
            });
            img.addEventListener('error', () => {
                loadedCount++;
                if (loadedCount === totalImages) {
                    scrollToBottom();
                }
            });
        }
    });

    // 如果所有图片都已经加载完成，立即滚动
    if (loadedCount === totalImages) {
        requestAnimationFrame(() => {
            scrollToBottom();
        });
    }
}

/**
 * 更新连接状态
 */
function updateStatus(connected) {
    const indicator = elements.statusIndicator;
    if (connected) {
        indicator.innerHTML = `
            <span class="w-2 h-2 bg-green-400 rounded-full mr-2 animate-pulse"></span>
            已连接
        `;
    } else {
        indicator.innerHTML = `
            <span class="w-2 h-2 bg-red-400 rounded-full mr-2"></span>
            未连接
        `;
    }
}

/**
 * 设置加载状态
 */
function setLoading(loading) {
    state.isLoading = loading;
    elements.sendButton.disabled = loading;

    if (loading) {
        elements.sendButton.innerHTML = `
            <div class="w-5 h-5 border-2 border-white border-t-transparent rounded-full animate-spin"></div>
        `;
    } else {
        elements.sendButton.innerHTML = `
            <svg class="w-5 h-5" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M12 19l9 2-9-18-9 18 9-2zm0 0v-8"></path>
            </svg>
        `;
    }
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

    elements.toast.className = `fixed top-4 right-4 px-4 py-3 rounded-lg shadow-lg z-50 transition-all duration-300 ${bgColors[type]} text-white`;
    elements.toast.textContent = message;
    elements.toast.classList.remove('hidden');

    setTimeout(() => {
        elements.toast.classList.add('hidden');
    }, 3000);
}

/**
 * 处理输入
 */
function handleInput() {
    const length = elements.messageInput.value.length;
    elements.charCount.textContent = `${length} / ${CONFIG.maxChars}`;

    if (length > CONFIG.maxChars) {
        elements.charCount.classList.add('text-red-500');
    } else {
        elements.charCount.classList.remove('text-red-500');
    }
}

/**
 * 处理键盘事件
 */
function handleKeydown(event) {
    if (event.key === 'Enter' && !event.shiftKey) {
        event.preventDefault();
        sendMessage();
    }
}

/**
 * 自动调整文本框高度
 */
function autoResizeTextarea() {
    const textarea = elements.messageInput;
    textarea.style.height = 'auto';
    textarea.style.height = Math.min(textarea.scrollHeight, 120) + 'px';
}

// ==================== 启动应用 ====================
document.addEventListener('DOMContentLoaded', init);