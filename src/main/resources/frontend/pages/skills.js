const API_BASE = 'http://localhost:12345/api';

async function init() {
    await loadSkills();
}

async function loadSkills() {
    try {
        const response = await RequestUtil.get('/skills', { showError: false });
        const skills = response.skills || [];

        if (skills.length === 0) {
            document.getElementById('skillsContent').innerHTML = `<div class="text-center text-gray-500 p-4">暂无技能</div>`;
            return;
        }

        document.getElementById('skillsContent').innerHTML = skills.map(skill => `
            <div class="bg-gray-50 rounded-lg p-4 mb-3 card-hover">
                <div class="flex justify-between items-start">
                    <div class="flex-1">
                        <div class="flex items-center space-x-2">
                            <span class="text-xs font-semibold px-2 py-0.5 rounded-full bg-blue-100 text-blue-700">
                                外部技能
                            </span>
                            <h3 class="font-medium text-gray-800">${skill.name}</h3>
                            <span class="px-2 py-0.5 text-xs rounded-full ${skill.enabled ? 'bg-green-100 text-green-600' : 'bg-gray-100 text-gray-600'}">
                                ${skill.enabled ? '已启用' : '已禁用'}
                            </span>
                        </div>
                        <p class="text-sm text-gray-600 mt-1">${skill.description || ''}</p>
                        ${skill.tools && skill.tools.length > 0 ?
                            `<div class="mt-2 flex flex-wrap gap-1">
                                ${skill.tools.map(tool =>
                                    `<span class="text-xs px-2 py-0.5 bg-gray-200 text-gray-600 rounded">${tool}</span>`
                                ).join('')}
                            </div>` : ''}
                    </div>
                    <div class="flex space-x-2 ml-4">
                        ${skill.enabled
                            ? `<button onclick="toggleSkill('${skill.name}', false)" class="text-sm px-3 py-1 bg-gray-200 hover:bg-gray-300 text-gray-700 rounded-lg">禁用</button>`
                            : `<button onclick="toggleSkill('${skill.name}', true)" class="text-sm px-3 py-1 bg-green-500 hover:bg-green-600 text-white rounded-lg">启用</button>`
                        }
                        <button onclick="deleteSkill('${skill.name}')" class="text-sm px-3 py-1 bg-red-500 hover:bg-red-600 text-white rounded-lg">删除</button>
                    </div>
                </div>
            </div>
        `).join('');
    } catch (e) {
        document.getElementById('skillsContent').innerHTML = `<div class="text-center text-red-500 p-4">加载失败: ${e.message}</div>`;
    }
}

function openAddSkillModal() {
    document.getElementById('addSkillModal').classList.remove('hidden');
}

function closeAddSkillModal() {
    document.getElementById('addSkillModal').classList.add('hidden');
    document.getElementById('addSkillForm').reset();
}

async function addSkill(event) {
    event.preventDefault();

    const name = document.getElementById('skillName').value;
    const description = document.getElementById('skillDescription').value;

    try {
        await RequestUtil.post('/skills', { name, description });
        closeAddSkillModal();
        loadSkills();
    } catch (e) {
        alert('添加失败: ' + e.message);
    }
}

async function toggleSkill(name, enabled) {
    try {
        const action = enabled ? 'enable' : 'disable';
        await RequestUtil.post(`/skills/${name}/${action}`);
        loadSkills();
    } catch (e) {
        alert('操作失败: ' + e.message);
    }
}

async function deleteSkill(name) {
    if (!confirm(`确定要删除技能 "${name}" 吗？`)) {
        return;
    }

    try {
        await RequestUtil.delete(`/skills/${name}`);
        loadSkills();
    } catch (e) {
        alert('删除失败: ' + e.message);
    }
}

document.addEventListener('DOMContentLoaded', init);
