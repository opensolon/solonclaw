async function init() {
    await loadLogs();
    setInterval(loadLogs, 5000);
}

async function loadLogs() {
    try {
        const text = await fetch('/api/logs/raw?lines=500').then(r => r.text());
        const el = document.getElementById('logsContent');
        if (!text || text.trim() === '') {
            el.textContent = '暂无日志';
        } else {
            el.textContent = text;
        }
    } catch (e) {
        document.getElementById('logsContent').textContent = '加载失败: ' + e.message;
    }
}

document.addEventListener('DOMContentLoaded', init);
