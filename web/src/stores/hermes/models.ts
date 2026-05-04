import { defineStore } from 'pinia'
import { ref, computed } from 'vue'
import * as systemApi from '@/api/hermes/system'
import type { AvailableModelGroup, CustomProvider, FallbackProvider } from '@/api/hermes/system'
import { useAppStore } from './app'

export const useModelsStore = defineStore('models', () => {
  const providers = ref<AvailableModelGroup[]>([])
  const allProviders = ref<AvailableModelGroup[]>([])
  const fallbackProviders = ref<FallbackProvider[]>([])
  const defaultModel = ref('')
  const defaultProvider = ref('')
  const loading = ref(false)

  const allModels = computed(() =>
    providers.value.flatMap(g =>
      g.models.map(m => ({
        id: m,
        provider: g.provider,
        label: g.label,
        base_url: g.base_url,
        dialect: g.dialect,
        isDefault: m === defaultModel.value,
      })),
    ),
  )

  async function fetchProviders() {
    loading.value = true
    try {
      const res = await systemApi.fetchAvailableModels()
      providers.value = res.groups
      allProviders.value = res.allProviders
      defaultModel.value = res.default
      defaultProvider.value = res.default_provider
      fallbackProviders.value = res.fallbackProviders
    } catch (err) {
      console.error('Failed to fetch providers:', err)
    } finally {
      loading.value = false
    }
  }

  async function setDefaultModel(modelId: string, provider: string) {
    await systemApi.updateDefaultModel({ default: modelId, provider })
    defaultModel.value = modelId
    const appStore = useAppStore()
    appStore.loadModels()
  }

  async function addProvider(data: CustomProvider) {
    await systemApi.addCustomProvider(data)
    await fetchProviders()
    const appStore = useAppStore()
    appStore.loadModels()
  }

  async function fetchProviderModels(data: { providerKey?: string; baseUrl: string; apiKey?: string; dialect: string }) {
    return systemApi.fetchProviderModels(data)
  }

  async function updateProvider(providerKey: string, data: {
    name?: string
    baseUrl?: string
    apiKey?: string
    defaultModel?: string
    dialect?: string
  }) {
    await systemApi.updateProvider(providerKey, data)
    await fetchProviders()
    const appStore = useAppStore()
    appStore.loadModels()
  }

  async function saveFallbackProviders(next: FallbackProvider[]) {
    await systemApi.updateFallbackProviders(next)
    fallbackProviders.value = next
    await fetchProviders()
  }

  async function removeProvider(providerKey: string) {
    await systemApi.removeCustomProvider(providerKey)
    await fetchProviders()
    const appStore = useAppStore()
    appStore.loadModels()
  }

  return {
    providers,
    allProviders,
    fallbackProviders,
    defaultModel,
    defaultProvider,
    loading,
    allModels,
    fetchProviders,
    setDefaultModel,
    addProvider,
    fetchProviderModels,
    updateProvider,
    saveFallbackProviders,
    removeProvider,
  }
})
