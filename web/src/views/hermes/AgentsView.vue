<script setup lang="ts">
import { computed, onMounted, reactive, ref, watch } from 'vue'
import { NButton, NCheckbox, NForm, NFormItem, NInput, NModal, NSelect, NSwitch, NTag, useDialog, useMessage } from 'naive-ui'
import { useAgentsStore } from '@/stores/hermes/agents'
import { useChatStore } from '@/stores/hermes/chat'
import { useModelsStore } from '@/stores/hermes/models'
import type { HermesAgent } from '@/api/hermes/agents'

const agentsStore = useAgentsStore()
const chatStore = useChatStore()
const modelsStore = useModelsStore()
const message = useMessage()
const dialog = useDialog()

const showCreateModal = ref(false)
const createName = ref('')
const createRole = ref('')

const selectedName = computed({
  get: () => agentsStore.selectedAgentName,
  set: value => {
    agentsStore.selectedAgentName = value
  },
})

const form = reactive({
  display_name: '',
  description: '',
  role_prompt: '',
  default_model: '',
  memory: '',
  allowed_tools_json: '[]',
  skills_json: '[]',
  enabled: true,
})

const selectedAgent = computed(() => agentsStore.selectedAgent)
const isReadonly = computed(() => !!selectedAgent.value?.readonly)

const modelOptions = computed(() => {
  const seen = new Set<string>()
  const options = [{ label: '使用全局默认模型', value: '' }]
  for (const model of modelsStore.allModels) {
    if (!model.id || seen.has(model.id)) continue
    seen.add(model.id)
    options.push({
      label: model.provider ? `${model.id} (${model.provider})` : model.id,
      value: model.id,
    })
  }
  if (form.default_model && !seen.has(form.default_model)) {
    options.push({ label: form.default_model, value: form.default_model })
  }
  return options
})

const toolOptions = computed(() => parseJsonList(form.allowed_tools_json).map(name => ({
  label: name,
  value: name,
})))

const skillOptions = computed(() => parseJsonList(form.skills_json).map(name => ({
  label: name,
  value: name,
})))

function parseJsonList(raw: string): string[] {
  if (!raw?.trim()) return []
  try {
    const parsed = JSON.parse(raw)
    if (Array.isArray(parsed)) {
      return parsed.map(item => String(item)).filter(Boolean)
    }
  } catch {
    // keep invalid input visible in the textarea; chips are best-effort only
  }
  return []
}

function copyAgent(agent: HermesAgent | null) {
  form.display_name = agent?.display_name || agent?.name || ''
  form.description = agent?.description || ''
  form.role_prompt = agent?.role_prompt || ''
  form.default_model = agent?.default_model || ''
  form.memory = agent?.memory || ''
  form.allowed_tools_json = agent?.allowed_tools_json || '[]'
  form.skills_json = agent?.skills_json || '[]'
  form.enabled = agent?.enabled !== false
}

function formatTime(ms?: number) {
  if (!ms) return '-'
  return new Date(ms).toLocaleString('zh-CN')
}

async function load() {
  await Promise.all([
    agentsStore.fetchAgents(chatStore.activeSessionId),
    modelsStore.fetchProviders(),
  ])
  if (agentsStore.selectedAgentName) {
    await agentsStore.fetchAgent(agentsStore.selectedAgentName, chatStore.activeSessionId)
  }
  copyAgent(selectedAgent.value)
}

async function selectAgent(name: string) {
  agentsStore.selectedAgentName = name
  await agentsStore.fetchAgent(name, chatStore.activeSessionId)
  copyAgent(selectedAgent.value)
}

async function saveAgent() {
  const agent = selectedAgent.value
  if (!agent || agent.readonly) {
    message.warning('default 是内置 Agent，不支持编辑')
    return
  }
  try {
    JSON.parse(form.allowed_tools_json || '[]')
    JSON.parse(form.skills_json || '[]')
  } catch {
    message.warning('工具和技能必须是 JSON 数组')
    return
  }
  try {
    await agentsStore.updateAgent(agent.name, {
      display_name: form.display_name,
      description: form.description,
      role_prompt: form.role_prompt,
      default_model: form.default_model,
      memory: form.memory,
      allowed_tools_json: form.allowed_tools_json,
      skills_json: form.skills_json,
      enabled: form.enabled,
    })
    message.success('Agent 已保存')
    await agentsStore.fetchAgents(chatStore.activeSessionId)
    await selectAgent(agent.name)
  } catch (err: any) {
    message.error(err?.message || '保存 Agent 失败')
  }
}

async function createAgent() {
  if (!createName.value.trim()) {
    message.warning('请输入 Agent 名称')
    return
  }
  try {
    const created = await agentsStore.createAgent({
      name: createName.value.trim(),
      role_prompt: createRole.value.trim(),
    })
    message.success(`已创建 Agent：${created.name}`)
    showCreateModal.value = false
    createName.value = ''
    createRole.value = ''
    await selectAgent(created.name)
  } catch (err: any) {
    message.error(err?.message || '创建 Agent 失败')
  }
}

function confirmDelete() {
  const agent = selectedAgent.value
  if (!agent || agent.readonly) {
    message.warning('default 是内置 Agent，不支持删除')
    return
  }
  dialog.warning({
    title: '删除 Agent',
    content: `确定删除 Agent「${agent.name}」吗？不会修改全局 config.yml。`,
    positiveText: '删除',
    negativeText: '取消',
    onPositiveClick: async () => {
      try {
        await agentsStore.deleteAgent(agent.name)
        message.success('Agent 已删除')
        await agentsStore.fetchAgents(chatStore.activeSessionId)
        if (agentsStore.selectedAgentName) {
          await selectAgent(agentsStore.selectedAgentName)
        } else {
          copyAgent(null)
        }
      } catch (err: any) {
        message.error(err?.message || '删除 Agent 失败')
      }
    },
  })
}

async function activateSelected() {
  const agent = selectedAgent.value
  if (!agent || !chatStore.activeSessionId) {
    message.warning('请先选择一个会话')
    return
  }
  try {
    await agentsStore.activateAgent(agent.name, chatStore.activeSessionId)
    message.success(`当前会话已切换到 Agent：${agent.name}`)
    await agentsStore.fetchAgents(chatStore.activeSessionId)
  } catch (err: any) {
    message.error(err?.message || '切换 Agent 失败')
  }
}

watch(() => chatStore.activeSessionId, async sessionId => {
  await agentsStore.fetchAgents(sessionId)
})

watch(selectedAgent, agent => {
  copyAgent(agent)
})

onMounted(load)
</script>

<template>
  <div class="agents-view">
    <header class="page-header">
      <div>
        <h2 class="header-title">Agents</h2>
        <p class="header-subtitle">定义可复用的角色、模型、工具、技能和记忆。Provider、渠道和全局配置仍共享。</p>
      </div>
      <div class="header-actions">
        <NButton size="small" :loading="agentsStore.loading" @click="load">刷新</NButton>
        <NButton type="primary" size="small" @click="showCreateModal = true">新建 Agent</NButton>
      </div>
    </header>

    <main class="agents-layout">
      <aside class="agent-list">
        <button
          v-for="agent in agentsStore.agents"
          :key="agent.name"
          class="agent-row"
          :class="{ selected: agent.name === selectedName, active: agent.active }"
          @click="selectAgent(agent.name)"
        >
          <span class="agent-row-title">
            <strong>{{ agent.display_name || agent.name }}</strong>
            <NTag v-if="agent.default_agent" size="tiny" :bordered="false">内置</NTag>
            <NTag v-if="agent.active" size="tiny" type="success" :bordered="false">当前</NTag>
          </span>
          <span class="agent-row-name">{{ agent.name }}</span>
          <span class="agent-row-meta">{{ agent.default_model || '全局默认模型' }}</span>
        </button>
      </aside>

      <section class="agent-editor">
        <div v-if="!selectedAgent" class="empty">请选择 Agent</div>
        <template v-else>
          <div class="editor-head">
            <div>
              <h3>{{ selectedAgent.display_name || selectedAgent.name }}</h3>
              <p>{{ selectedAgent.readonly ? 'default 映射 runtime 根目录，只读展示。' : '这些设置只影响该命名 Agent，不会写入全局配置。' }}</p>
            </div>
            <div class="editor-actions">
              <NButton size="small" :disabled="selectedAgent.active || !selectedAgent.enabled" :loading="agentsStore.activating" @click="activateSelected">
                设为当前会话 Agent
              </NButton>
              <NButton size="small" type="error" secondary :disabled="isReadonly" @click="confirmDelete">删除</NButton>
              <NButton type="primary" size="small" :disabled="isReadonly" :loading="agentsStore.saving" @click="saveAgent">保存</NButton>
            </div>
          </div>

          <NForm label-placement="top" class="agent-form">
            <div class="form-grid">
              <NFormItem label="显示名称">
                <NInput v-model:value="form.display_name" :disabled="isReadonly" placeholder="例如 代码助手" />
              </NFormItem>
              <NFormItem label="默认模型">
                <NSelect
                  v-model:value="form.default_model"
                  :options="modelOptions"
                  filterable
                  tag
                  :disabled="isReadonly"
                  placeholder="使用全局默认模型"
                />
              </NFormItem>
            </div>

            <NFormItem label="说明">
              <NInput v-model:value="form.description" :disabled="isReadonly" placeholder="简短说明这个 Agent 的用途" />
            </NFormItem>

            <NFormItem label="角色设定">
              <NInput
                v-model:value="form.role_prompt"
                type="textarea"
                :autosize="{ minRows: 7, maxRows: 14 }"
                :disabled="isReadonly"
                placeholder="写入这个 Agent 的角色、工作方式和边界"
              />
            </NFormItem>

            <div class="form-grid">
              <NFormItem label="工具 JSON">
                <NInput
                  v-model:value="form.allowed_tools_json"
                  type="textarea"
                  :autosize="{ minRows: 4, maxRows: 8 }"
                  :disabled="isReadonly"
                  placeholder='["read_file","write_file"]'
                />
                <div v-if="toolOptions.length" class="chips">
                  <NTag v-for="tool in toolOptions" :key="tool.value" size="small" :bordered="false">{{ tool.label }}</NTag>
                </div>
              </NFormItem>
              <NFormItem label="技能 JSON">
                <NInput
                  v-model:value="form.skills_json"
                  type="textarea"
                  :autosize="{ minRows: 4, maxRows: 8 }"
                  :disabled="isReadonly"
                  placeholder='["java","review"]'
                />
                <div v-if="skillOptions.length" class="chips">
                  <NTag v-for="skill in skillOptions" :key="skill.value" size="small" :bordered="false">{{ skill.label }}</NTag>
                </div>
              </NFormItem>
            </div>

            <NFormItem label="记忆">
              <NInput
                v-model:value="form.memory"
                type="textarea"
                :autosize="{ minRows: 5, maxRows: 10 }"
                :disabled="isReadonly"
                placeholder="记录这个 Agent 需要长期保留的偏好、背景和结论"
              />
            </NFormItem>

            <NFormItem v-if="!isReadonly" label="启用">
              <NSwitch v-model:value="form.enabled" />
            </NFormItem>
            <NCheckbox v-else :checked="true" disabled>default Agent 始终启用</NCheckbox>
          </NForm>
        </template>
      </section>

      <aside class="agent-status">
        <h3>状态</h3>
        <div class="status-row">
          <span>当前会话</span>
          <strong>{{ agentsStore.activeAgentName || 'default' }}</strong>
        </div>
        <div class="status-row">
          <span>运行中任务</span>
          <strong>{{ selectedAgent?.running_runs || 0 }}</strong>
        </div>
        <div class="status-row path-row">
          <span>workspace</span>
          <code>{{ selectedAgent?.workspace_path || '-' }}</code>
        </div>
        <div class="status-row path-row">
          <span>skills</span>
          <code>{{ selectedAgent?.skills_path || '-' }}</code>
        </div>
        <div class="status-row path-row">
          <span>cache</span>
          <code>{{ selectedAgent?.cache_path || '-' }}</code>
        </div>

        <h3>最近运行</h3>
        <div v-if="!selectedAgent?.recent_runs?.length" class="empty small">暂无运行记录</div>
        <div v-for="run in selectedAgent?.recent_runs || []" :key="run.run_id" class="run-row">
          <span class="run-status">{{ run.status }}</span>
          <span>{{ run.model || '-' }}</span>
          <small>{{ formatTime(run.started_at) }}</small>
        </div>
      </aside>
    </main>

    <NModal
      v-model:show="showCreateModal"
      preset="card"
      title="新建 Agent"
      :style="{ width: 'min(520px, calc(100vw - 32px))' }"
    >
      <NForm label-placement="top">
        <NFormItem label="名称" required>
          <NInput v-model:value="createName" placeholder="例如 coder，仅限字母、数字、点、下划线和短横线" />
        </NFormItem>
        <NFormItem label="角色设定">
          <NInput
            v-model:value="createRole"
            type="textarea"
            :autosize="{ minRows: 4, maxRows: 8 }"
            placeholder="留空则使用默认角色设定"
          />
        </NFormItem>
      </NForm>
      <template #footer>
        <div class="modal-footer">
          <NButton @click="showCreateModal = false">取消</NButton>
          <NButton type="primary" :loading="agentsStore.saving" @click="createAgent">创建</NButton>
        </div>
      </template>
    </NModal>
  </div>
</template>

<style scoped lang="scss">
@use '@/styles/variables' as *;

.agents-view {
  height: calc(100 * var(--vh));
  display: flex;
  flex-direction: column;
  min-width: 0;
}

.header-subtitle {
  margin: 4px 0 0;
  color: $text-muted;
  font-size: 13px;
}

.header-actions,
.editor-actions,
.modal-footer {
  display: flex;
  align-items: center;
  gap: 8px;
}

.agents-layout {
  flex: 1;
  min-height: 0;
  display: grid;
  grid-template-columns: 260px minmax(420px, 1fr) 300px;
  gap: 14px;
  padding: 20px;
  overflow: hidden;
}

.agent-list,
.agent-editor,
.agent-status {
  min-height: 0;
  overflow: auto;
  border: 1px solid $border-color;
  border-radius: $radius-sm;
  background: $bg-primary;
}

.agent-list {
  padding: 8px;
}

.agent-row {
  width: 100%;
  display: grid;
  gap: 4px;
  padding: 11px 10px;
  border: 1px solid transparent;
  border-radius: $radius-sm;
  background: transparent;
  color: $text-primary;
  text-align: left;
  cursor: pointer;
  margin-bottom: 4px;

  &:hover {
    background: rgba(var(--accent-primary-rgb), 0.05);
  }

  &.selected {
    border-color: $accent-primary;
    background: rgba(var(--accent-primary-rgb), 0.08);
  }
}

.agent-row-title {
  display: flex;
  align-items: center;
  gap: 6px;
  min-width: 0;

  strong {
    min-width: 0;
    overflow: hidden;
    text-overflow: ellipsis;
    white-space: nowrap;
  }
}

.agent-row-name,
.agent-row-meta {
  font-size: 12px;
  color: $text-muted;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.agent-editor {
  padding: 16px;
}

.editor-head {
  display: flex;
  justify-content: space-between;
  gap: 14px;
  margin-bottom: 16px;

  h3 {
    margin: 0 0 4px;
    font-size: 18px;
  }

  p {
    margin: 0;
    color: $text-muted;
    font-size: 13px;
  }
}

.agent-form {
  max-width: 920px;
}

.form-grid {
  display: grid;
  grid-template-columns: minmax(0, 1fr) minmax(0, 1fr);
  gap: 12px;
}

.chips {
  display: flex;
  flex-wrap: wrap;
  gap: 6px;
  margin-top: 8px;
}

.agent-status {
  padding: 14px;

  h3 {
    margin: 0 0 12px;
    font-size: 14px;
  }

  h3:not(:first-child) {
    margin-top: 18px;
  }
}

.status-row {
  display: grid;
  gap: 4px;
  padding: 9px 0;
  border-bottom: 1px solid rgba(var(--text-muted-rgb), 0.12);

  span {
    color: $text-muted;
    font-size: 12px;
  }
}

.path-row code {
  display: block;
  color: $text-secondary;
  font-family: $font-code;
  font-size: 11px;
  white-space: normal;
  overflow-wrap: anywhere;
}

.run-row {
  display: grid;
  gap: 4px;
  padding: 10px;
  margin-bottom: 8px;
  border: 1px solid rgba(var(--text-muted-rgb), 0.18);
  border-radius: $radius-sm;

  small {
    color: $text-muted;
  }
}

.run-status {
  font-family: $font-code;
  font-size: 12px;
  color: $accent-primary;
}

.empty {
  color: $text-muted;
  padding: 28px 0;
  text-align: center;
}

.empty.small {
  padding: 12px 0;
  font-size: 13px;
}

@media (max-width: 1180px) {
  .agents-layout {
    grid-template-columns: 220px minmax(0, 1fr);
  }

  .agent-status {
    grid-column: 1 / -1;
  }
}

@media (max-width: $breakpoint-mobile) {
  .agents-layout {
    grid-template-columns: 1fr;
    overflow: auto;
  }

  .editor-head,
  .form-grid {
    grid-template-columns: 1fr;
    flex-direction: column;
  }

  .editor-actions {
    flex-wrap: wrap;
  }
}
</style>
