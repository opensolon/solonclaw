<script setup lang="ts">
import { computed, onMounted, watch } from 'vue'
import { NSelect, useMessage } from 'naive-ui'
import { useAgentsStore } from '@/stores/hermes/agents'

const props = defineProps<{
  sessionId?: string | null
}>()

const message = useMessage()
const agentsStore = useAgentsStore()

const options = computed(() =>
  [
    {
      label: '默认 Agent',
      value: 'default',
      disabled: false,
    },
    ...agentsStore.agents.map(agent => ({
      label: agent.display_name && agent.display_name !== agent.name
        ? `${agent.display_name} (${agent.name})`
        : agent.name,
      value: agent.name,
      disabled: !agent.enabled,
    })),
  ],
)

async function load() {
  await agentsStore.fetchAgents(props.sessionId || undefined)
}

async function handleChange(value: string | number | Array<string | number>) {
  if (typeof value !== 'string' || value === agentsStore.activeAgentName) return
  if (!props.sessionId) {
    message.warning('请先选择一个会话')
    return
  }
  try {
    await agentsStore.activateAgent(value, props.sessionId)
    message.success(`当前会话已切换到 Agent：${value}`)
  } catch (err: any) {
    message.error(err?.message || '切换 Agent 失败')
  }
}

onMounted(load)
watch(() => props.sessionId, load)
</script>

<template>
  <div class="agent-selector">
    <span class="agent-selector-label">当前 Agent</span>
    <NSelect
      :value="agentsStore.activeAgentName"
      :options="options"
      :loading="agentsStore.loading || agentsStore.activating"
      size="small"
      class="agent-selector-control"
      @update:value="handleChange"
    />
  </div>
</template>

<style scoped lang="scss">
@use '@/styles/variables' as *;

.agent-selector {
  display: flex;
  align-items: center;
  gap: 8px;
  min-width: 0;
}

.agent-selector-label {
  font-size: 12px;
  color: $text-muted;
  white-space: nowrap;
}

.agent-selector-control {
  width: 170px;
}

@media (max-width: $breakpoint-mobile) {
  .agent-selector-label {
    display: none;
  }

  .agent-selector-control {
    width: 130px;
  }
}
</style>
