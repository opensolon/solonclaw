<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { NButton, NSelect, NSpin } from 'naive-ui'
import { fetchSessions, fetchSessionCheckpoints, fetchSessionTree, rollbackCheckpoint } from '@/api/hermes/sessions'
import { fetchRunEvents, fetchSessionRuns, type AgentRun, type AgentRunEvent } from '@/api/hermes/runs'

const sessions = ref<any[]>([])
const runs = ref<AgentRun[]>([])
const events = ref<AgentRunEvent[]>([])
const checkpoints = ref<any[]>([])
const tree = ref<any>(null)
const selectedSessionId = ref('')
const selectedRunId = ref('')
const loading = ref(false)
const rollingBack = ref('')

const sessionOptions = computed(() => sessions.value.map(session => ({
  label: session.title || session.preview || session.id,
  value: session.id,
})))

async function loadSessions() {
  sessions.value = await fetchSessions(undefined, 200)
  if (!selectedSessionId.value && sessions.value.length) {
    selectedSessionId.value = sessions.value[0].id
  }
}

async function loadSessionDetail() {
  if (!selectedSessionId.value) return
  loading.value = true
  try {
    const [loadedRuns, loadedTree, loadedCheckpoints] = await Promise.all([
      fetchSessionRuns(selectedSessionId.value, 30),
      fetchSessionTree(selectedSessionId.value),
      fetchSessionCheckpoints(selectedSessionId.value),
    ])
    runs.value = loadedRuns
    tree.value = loadedTree
    checkpoints.value = loadedCheckpoints
    selectedRunId.value = loadedRuns[0]?.run_id || ''
    events.value = selectedRunId.value ? await fetchRunEvents(selectedRunId.value) : []
  } finally {
    loading.value = false
  }
}

async function loadEvents(runId: string) {
  selectedRunId.value = runId
  events.value = runId ? await fetchRunEvents(runId) : []
}

async function handleRollback(id: string) {
  rollingBack.value = id
  try {
    await rollbackCheckpoint(id)
    await loadSessionDetail()
  } finally {
    rollingBack.value = ''
  }
}

function time(ms?: number) {
  if (!ms) return '-'
  return new Date(ms).toLocaleString('zh-CN')
}

onMounted(async () => {
  await loadSessions()
  await loadSessionDetail()
})
</script>

<template>
  <div class="runs-view">
    <header class="page-header">
      <h2 class="header-title">运行</h2>
      <div class="header-actions">
        <NSelect v-model:value="selectedSessionId" :options="sessionOptions" size="small" class="session-select" @update:value="loadSessionDetail" />
        <NButton size="small" :loading="loading" @click="loadSessionDetail">刷新</NButton>
      </div>
    </header>

    <NSpin :show="loading">
      <main class="runs-layout">
        <section class="panel">
          <h3>运行记录</h3>
          <button v-for="run in runs" :key="run.run_id" class="run-row" :class="{ active: run.run_id === selectedRunId }" @click="loadEvents(run.run_id)">
            <span class="run-status" :class="run.status">{{ run.status }}</span>
            <span>{{ run.provider || '-' }}/{{ run.model || '-' }}</span>
            <span>{{ run.attempts }} 次</span>
            <small>{{ time(run.started_at) }}</small>
            <p>{{ run.final_reply_preview || run.input_preview || run.error }}</p>
          </button>
          <div v-if="runs.length === 0" class="empty">暂无运行记录</div>
        </section>

        <section class="panel">
          <h3>事件时间线</h3>
          <div v-for="event in events" :key="event.event_id" class="event-row">
            <span class="event-type">{{ event.event_type }}</span>
            <span>{{ event.summary }}</span>
            <small>#{{ event.attempt_no }} · {{ time(event.created_at) }}</small>
          </div>
          <div v-if="events.length === 0" class="empty">暂无事件</div>
        </section>

        <section class="panel side-panel">
          <h3>会话树</h3>
          <div v-for="node in tree?.nodes || []" :key="node.id" class="mini-row">
            <span>{{ node.branch_name || 'main' }}</span>
            <small>{{ node.id }}</small>
          </div>
          <h3>Checkpoint</h3>
          <div v-for="checkpoint in checkpoints" :key="checkpoint.checkpoint_id" class="mini-row">
            <span>{{ time(checkpoint.created_at) }}</span>
            <NButton size="tiny" secondary :loading="rollingBack === checkpoint.checkpoint_id" @click="handleRollback(checkpoint.checkpoint_id)">回滚</NButton>
          </div>
        </section>
      </main>
    </NSpin>
  </div>
</template>

<style scoped lang="scss">
@use '@/styles/variables' as *;

.runs-view {
  height: calc(100 * var(--vh));
  display: flex;
  flex-direction: column;
}

.header-actions {
  display: flex;
  gap: 8px;
  align-items: center;
}

.session-select {
  width: 320px;
}

.runs-layout {
  display: grid;
  grid-template-columns: minmax(280px, 0.9fr) minmax(360px, 1.2fr) minmax(260px, 0.8fr);
  gap: 16px;
  padding: 20px;
  min-height: 0;
}

.panel {
  min-height: 520px;
  border: 1px solid $border-color;
  border-radius: $radius-sm;
  background: $bg-primary;
  overflow: auto;
  padding: 14px;
}

h3 {
  margin: 0 0 12px;
  font-size: 14px;
}

.run-row,
.event-row,
.mini-row {
  width: 100%;
  border: 1px solid rgba($border-color, 0.7);
  border-radius: $radius-sm;
  background: transparent;
  color: $text-primary;
  text-align: left;
  padding: 10px;
  margin-bottom: 8px;
}

.run-row {
  display: grid;
  gap: 5px;
  cursor: pointer;
}

.run-row.active {
  border-color: $accent-primary;
  background: rgba(var(--accent-primary-rgb), 0.08);
}

.run-row p,
.event-row small,
.mini-row small {
  margin: 0;
  color: $text-muted;
}

.event-row,
.mini-row {
  display: flex;
  flex-direction: column;
  gap: 4px;
}

.run-status,
.event-type {
  font-family: $font-code;
  font-size: 12px;
  color: $accent-primary;
}

.empty {
  color: $text-muted;
  padding: 24px 0;
  text-align: center;
}

@media (max-width: 1100px) {
  .runs-layout {
    grid-template-columns: 1fr;
  }
}
</style>
