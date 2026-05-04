<script setup lang="ts">
import { computed, ref } from 'vue'
import { NModal, NForm, NFormItem, NInput, NButton, NSelect, useMessage } from 'naive-ui'
import { useModelsStore } from '@/stores/hermes/models'
import type { AvailableModelGroup } from '@/api/hermes/system'
import { useI18n } from 'vue-i18n'

const { t } = useI18n()

const props = defineProps<{
  provider?: AvailableModelGroup | null
}>()

const emit = defineEmits<{
  close: []
  saved: []
}>()

const modelsStore = useModelsStore()
const message = useMessage()

const showModal = ref(true)
const loading = ref(false)
const modelsLoading = ref(false)
const modelOptions = ref<Array<{ label: string; value: string }>>([])
const isEdit = computed(() => !!props.provider)
const formData = ref({
  providerKey: props.provider?.provider || '',
  name: props.provider?.label || '',
  baseUrl: props.provider?.base_url || '',
  apiKey: '',
  defaultModel: props.provider?.models?.[0] || '',
  dialect: props.provider?.dialect || 'openai-responses',
})

function baseUrlPlaceholder(): string {
  switch (formData.value.dialect) {
    case 'ollama':
      return 'http://127.0.0.1:11434'
    case 'gemini':
      return 'https://generativelanguage.googleapis.com'
    case 'anthropic':
      return 'https://api.anthropic.com'
    default:
      return 'https://api.example.com'
  }
}

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

const dialectOptions = [
  { label: dialectLabel('openai'), value: 'openai' },
  { label: dialectLabel('openai-responses'), value: 'openai-responses' },
  { label: dialectLabel('ollama'), value: 'ollama' },
  { label: dialectLabel('gemini'), value: 'gemini' },
  { label: dialectLabel('anthropic'), value: 'anthropic' },
]

async function handleSave() {
  if (!formData.value.providerKey.trim()) {
    message.warning(t('models.providerKeyRequired'))
    return
  }
  if (!formData.value.name.trim()) {
    message.warning(t('models.nameRequired'))
    return
  }
  if (!formData.value.baseUrl.trim()) {
    message.warning(t('models.baseUrlRequired'))
    return
  }
  if (!formData.value.defaultModel.trim()) {
    message.warning(t('models.modelRequired'))
    return
  }

  loading.value = true
  try {
    if (isEdit.value) {
      const nextProvider = {
        name: formData.value.name.trim(),
        baseUrl: formData.value.baseUrl.trim(),
        defaultModel: formData.value.defaultModel.trim(),
        dialect: formData.value.dialect,
        apiKey: formData.value.apiKey.trim() || undefined,
      }
      await modelsStore.updateProvider(formData.value.providerKey.trim(), {
        ...nextProvider,
      })
      message.success(t('models.providerUpdated'))
    } else {
      await modelsStore.addProvider({
        providerKey: formData.value.providerKey.trim(),
        name: formData.value.name.trim(),
        baseUrl: formData.value.baseUrl.trim(),
        apiKey: formData.value.apiKey.trim(),
        defaultModel: formData.value.defaultModel.trim(),
        dialect: formData.value.dialect,
      })
      message.success(t('models.providerAdded'))
    }
    emit('saved')
  } catch (e: any) {
    message.error(e.message)
  } finally {
    loading.value = false
  }
}

async function fetchModelList() {
  if (!formData.value.baseUrl.trim()) {
    message.warning(t('models.baseUrlRequired'))
    return
  }
  modelsLoading.value = true
  try {
    const res = await modelsStore.fetchProviderModels({
      providerKey: isEdit.value ? formData.value.providerKey.trim() : undefined,
      baseUrl: formData.value.baseUrl.trim(),
      apiKey: formData.value.apiKey.trim(),
      dialect: formData.value.dialect,
    })
    modelOptions.value = (res.models || []).map(model => ({ label: model, value: model }))
    if (!modelOptions.value.length) {
      message.warning(t('models.noRemoteModels'))
      return
    }
    if (!formData.value.defaultModel.trim()) {
      formData.value.defaultModel = modelOptions.value[0].value
    }
    message.success(t('models.modelsFetched'))
  } catch (e: any) {
    message.error(e.message || t('models.fetchModelsFailed'))
  } finally {
    modelsLoading.value = false
  }
}

function handleClose() {
  showModal.value = false
  setTimeout(() => emit('close'), 200)
}
</script>

<template>
  <NModal
    v-model:show="showModal"
    preset="card"
    :title="isEdit ? t('models.editProvider') : t('models.addProvider')"
    :style="{ width: 'min(560px, calc(100vw - 32px))' }"
    :mask-closable="!loading"
    @after-leave="emit('close')"
  >
    <NForm label-placement="top">
      <NFormItem :label="t('models.providerKey')" required>
        <NInput
          v-model:value="formData.providerKey"
          :placeholder="t('models.providerKeyPlaceholder')"
          :disabled="isEdit"
        />
      </NFormItem>

      <NFormItem :label="t('models.name')" required>
        <NInput
          v-model:value="formData.name"
          :placeholder="t('models.namePlaceholder')"
        />
      </NFormItem>

      <NFormItem :label="t('models.baseUrl')" required>
        <NInput
          v-model:value="formData.baseUrl"
          :placeholder="baseUrlPlaceholder()"
        />
      </NFormItem>

      <NFormItem :label="t('models.apiKey')">
        <NInput
          v-model:value="formData.apiKey"
          type="password"
          show-password-on="click"
          :placeholder="isEdit && provider?.has_api_key ? t('models.apiKeyConfigured') : t('models.apiKeyPlaceholder')"
          autocomplete="off"
        />
      </NFormItem>

      <NFormItem :label="t('models.defaultModel')" required>
        <div class="model-select-row">
          <NSelect
            v-model:value="formData.defaultModel"
            filterable
            tag
            :options="modelOptions"
            :placeholder="t('models.selectOrInput')"
          />
          <NButton :loading="modelsLoading" @click="fetchModelList">
            {{ t('models.fetchModelList') }}
          </NButton>
        </div>
      </NFormItem>

      <NFormItem :label="t('models.dialect')" required>
        <NSelect
          v-model:value="formData.dialect"
          :options="dialectOptions"
        />
      </NFormItem>
    </NForm>

    <template #footer>
      <div class="modal-footer">
        <NButton @click="handleClose">{{ t('common.cancel') }}</NButton>
        <NButton type="primary" :loading="loading" @click="handleSave">
          {{ isEdit ? t('common.save') : t('common.add') }}
        </NButton>
      </div>
    </template>
  </NModal>
</template>

<style scoped lang="scss">
.modal-footer {
  display: flex;
  justify-content: flex-end;
  gap: 8px;
}

.model-select-row {
  display: grid;
  grid-template-columns: 1fr auto;
  gap: 8px;
}
</style>
