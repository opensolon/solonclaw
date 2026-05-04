<script setup lang="ts">
import { ref, computed } from 'vue'
import { NButton, useMessage, useDialog } from 'naive-ui'
import type { AvailableModelGroup } from '@/api/hermes/system'
import { useModelsStore } from '@/stores/hermes/models'
import { useI18n } from 'vue-i18n'

const props = defineProps<{ provider: AvailableModelGroup }>()
const emit = defineEmits<{
  edit: [provider: AvailableModelGroup]
}>()

const { t } = useI18n()
const modelsStore = useModelsStore()
const message = useMessage()
const dialog = useDialog()

const displayName = computed(() => props.provider.label)
const deleting = ref(false)

function dialectLabel(value: string): string {
  switch (value) {
    case 'openai':
      return t('models.dialectOpenai')
    case 'openai-responses':
      return t('models.dialectOpenaiResponses')
    case 'ollama':
      return t('models.dialectOllama')
    case 'gemini':
      return t('models.dialectGemini')
    case 'anthropic':
      return t('models.dialectAnthropic')
    default:
      return value
  }
}

async function handleDelete() {
  dialog.warning({
    title: t('models.deleteProvider'),
    content: t('models.deleteConfirm', { name: displayName.value }),
    positiveText: t('common.delete'),
    negativeText: t('common.cancel'),
    onPositiveClick: async () => {
      deleting.value = true
      try {
        await modelsStore.removeProvider(props.provider.provider)
        message.success(t('models.providerDeleted'))
      } catch (e: any) {
        message.error(e.message)
      } finally {
        deleting.value = false
      }
    },
  })
}
</script>

<template>
  <div class="provider-card">
    <div class="card-header">
      <h3 class="provider-name">{{ displayName }}</h3>
      <span class="type-badge" :class="provider.isDefault ? 'default' : 'normal'">
        {{ provider.isDefault ? t('models.defaultBadge') : dialectLabel(provider.dialect) }}
      </span>
    </div>

    <div class="card-body">
      <div class="info-row">
        <span class="info-label">{{ t('models.providerKey') }}</span>
        <code class="info-value mono">{{ provider.provider }}</code>
      </div>
      <div class="info-row">
        <span class="info-label">{{ t('models.baseUrl') }}</span>
        <code class="info-value mono">{{ provider.base_url }}</code>
      </div>
      <div class="info-row">
        <span class="info-label">{{ t('models.defaultModel') }}</span>
        <code class="info-value mono">{{ provider.models[0] || '—' }}</code>
      </div>
      <div class="info-row">
        <span class="info-label">{{ t('models.apiKey') }}</span>
        <span class="info-value">{{ provider.has_api_key ? t('models.apiKeyConfigured') : t('models.apiKeyMissing') }}</span>
      </div>
    </div>

    <div class="card-actions">
      <NButton size="tiny" quaternary @click="emit('edit', provider)">{{ t('common.edit') }}</NButton>
      <NButton size="tiny" quaternary type="error" :loading="deleting" @click="handleDelete">{{ t('common.delete') }}</NButton>
    </div>
  </div>
</template>

<style scoped lang="scss">
@use '@/styles/variables' as *;

.provider-card {
  background-color: $bg-card;
  border: 1px solid $border-color;
  border-radius: $radius-md;
  padding: 16px;
  transition: border-color $transition-fast;

  &:hover {
    border-color: rgba(var(--accent-primary-rgb), 0.3);
  }
}

.card-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-bottom: 12px;
}

.provider-name {
  font-size: 15px;
  font-weight: 600;
  color: $text-primary;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
  max-width: 70%;
}

.type-badge {
  font-size: 11px;
  padding: 2px 8px;
  border-radius: 10px;
  font-weight: 500;

  &.default {
    background: rgba(var(--accent-primary-rgb), 0.12);
    color: $accent-primary;
  }

  &.normal {
    background: rgba(148, 163, 184, 0.12);
    color: $text-secondary;
  }
}

.card-body {
  display: flex;
  flex-direction: column;
  gap: 6px;
  margin-bottom: 14px;
}

.info-row {
  display: flex;
  justify-content: space-between;
  align-items: center;
}

.info-label {
  font-size: 12px;
  color: $text-muted;
}

.info-value {
  font-size: 12px;
  color: $text-secondary;
}

.mono {
  font-family: $font-code;
  font-size: 12px;
}

.card-actions {
  display: flex;
  gap: 8px;
  border-top: 1px solid $border-light;
  padding-top: 10px;
}
</style>
