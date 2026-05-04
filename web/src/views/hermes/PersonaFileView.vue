<script setup lang="ts">
import { computed, onMounted, ref, watch } from 'vue'
import { NButton, useMessage } from 'naive-ui'
import { useRoute } from 'vue-router'
import MarkdownRenderer from '@/components/hermes/chat/MarkdownRenderer.vue'
import { fetchPersonaFile, personaMeta, savePersonaFile, type PersonaFileData } from '@/api/hermes/persona'

const route = useRoute()
const message = useMessage()

const loading = ref(false)
const saving = ref(false)
const file = ref<PersonaFileData | null>(null)
const editing = ref(false)
const editContent = ref('')

const fileKey = computed(() => String(route.params.key || 'agents'))
const currentMeta = computed(() => personaMeta(fileKey.value))
const isEmpty = computed(() => !file.value?.content?.trim())
const isReadOnly = computed(() => fileKey.value === 'memory_today')

async function loadFile() {
  loading.value = true
  editing.value = false
  try {
    file.value = await fetchPersonaFile(fileKey.value)
    editContent.value = file.value.content || ''
  } catch (err: any) {
    message.error(err.message || '加载文件失败')
  } finally {
    loading.value = false
  }
}

function startEdit() {
  editContent.value = file.value?.content || ''
  editing.value = true
}

function cancelEdit() {
  editing.value = false
  editContent.value = file.value?.content || ''
}

async function handleSave() {
  saving.value = true
  try {
    await savePersonaFile(fileKey.value, editContent.value)
    await loadFile()
    editing.value = false
    message.success('已保存')
  } catch (err: any) {
    message.error(err.message || '保存失败')
  } finally {
    saving.value = false
  }
}

onMounted(loadFile)
watch(fileKey, loadFile)
</script>

<template>
  <div class="memory-view">
    <header class="page-header">
      <h2 class="header-title">{{ currentMeta.title }}</h2>
      <div class="page-actions">
        <NButton size="small" quaternary @click="loadFile">
          <template #icon>
            <svg width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
              <polyline points="23 4 23 10 17 10" />
              <path d="M20.49 15a9 9 0 1 1-2.12-9.36L23 10" />
            </svg>
          </template>
          刷新
        </NButton>
        <NButton v-if="!editing && !isReadOnly" size="small" quaternary @click="startEdit">
          <template #icon>
            <svg width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.5">
              <path d="M11 4H4a2 2 0 0 0-2 2v14a2 2 0 0 0 2 2h14a2 2 0 0 0 2-2v-7" />
              <path d="M18.5 2.5a2.121 2.121 0 0 1 3 3L12 15l-4 1 1-4 9.5-9.5z" />
            </svg>
          </template>
          编辑
        </NButton>
      </div>
    </header>

    <div class="memory-content">
      <div v-if="loading && !file" class="memory-loading">加载中...</div>
      <div v-else class="memory-sections single">
        <div class="memory-section">
          <div v-if="!editing" class="section-body">
            <MarkdownRenderer v-if="!isEmpty" :content="file?.content || ''" />
            <p v-else class="empty-text">暂无内容</p>
          </div>

          <div v-else class="section-edit">
            <textarea
              v-model="editContent"
              class="edit-textarea"
              :placeholder="`编辑 ${currentMeta.fileName}`"
              spellcheck="false"
            ></textarea>
            <div class="edit-actions">
              <NButton size="small" @click="cancelEdit">取消</NButton>
              <NButton size="small" type="primary" :loading="saving" @click="handleSave">保存</NButton>
            </div>
          </div>
        </div>
      </div>
    </div>
  </div>
</template>

<style scoped lang="scss">
@use '@/styles/variables' as *;

.memory-view {
  height: calc(100 * var(--vh));
  display: flex;
  flex-direction: column;
}

.memory-content {
  flex: 1;
  overflow: hidden;
  padding: 20px;
  display: flex;
  flex-direction: column;
}

.memory-loading {
  flex: 1;
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 13px;
  color: $text-muted;
}

.memory-sections {
  display: flex;
  flex: 1;
  min-height: 0;

  &.single {
    width: 100%;
  }
}

.memory-section {
  flex: 1;
  min-height: 0;
  border: 1px solid $border-color;
  border-radius: $radius-md;
  overflow: hidden;
  display: flex;
  flex-direction: column;
}

.section-body {
  flex: 1;
  overflow-y: auto;
  padding: 20px;
  min-height: 0;
}

.empty-text {
  color: $text-muted;
  font-style: italic;
  font-size: 13px;
}

.section-edit {
  flex: 1;
  display: flex;
  flex-direction: column;
  padding: 20px;
  min-height: 0;
}

.edit-textarea {
  flex: 1;
  width: 100%;
  min-height: 0;
  padding: 12px;
  border: 1px solid $border-color;
  border-radius: $radius-sm;
  background: $bg-input;
  color: $text-primary;
  font-family: $font-code;
  font-size: 13px;
  line-height: 1.6;
  resize: none;
  outline: none;

  &:focus {
    border-color: $accent-primary;
  }
}

.edit-actions {
  display: flex;
  justify-content: flex-end;
  gap: 8px;
  margin-top: 10px;
}

.page-actions {
  display: flex;
  gap: 8px;
}
</style>
