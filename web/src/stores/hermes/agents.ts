import { defineStore } from 'pinia'
import { computed, ref } from 'vue'
import * as agentsApi from '@/api/hermes/agents'
import type { AgentMutationPayload, HermesAgent } from '@/api/hermes/agents'

export const useAgentsStore = defineStore('agents', () => {
  const agents = ref<HermesAgent[]>([])
  const detailMap = ref<Record<string, HermesAgent>>({})
  const activeAgentName = ref('default')
  const selectedAgentName = ref('')
  const loading = ref(false)
  const saving = ref(false)
  const activating = ref(false)

  const selectedAgent = computed(() =>
    detailMap.value[selectedAgentName.value]
    || agents.value.find(agent => agent.name === selectedAgentName.value)
    || agents.value[0]
    || null,
  )

  const activeAgent = computed(() =>
    agents.value.find(agent => agent.name === activeAgentName.value)
    || agents.value.find(agent => agent.name === 'default')
    || null,
  )

  async function fetchAgents(sessionId?: string | null) {
    loading.value = true
    try {
      const res = await agentsApi.fetchAgents(sessionId)
      agents.value = (res.agents || []).filter(agent => agent.name !== 'default' && !agent.default_agent)
      activeAgentName.value = res.active_agent_name || 'default'
      if (!selectedAgentName.value || !agents.value.some(agent => agent.name === selectedAgentName.value)) {
        selectedAgentName.value = agents.value.some(agent => agent.name === activeAgentName.value)
          ? activeAgentName.value
          : agents.value[0]?.name || ''
      }
      for (const agent of agents.value) {
        detailMap.value[agent.name] = {
          ...(detailMap.value[agent.name] || {}),
          ...agent,
        }
      }
      return agents.value
    } finally {
      loading.value = false
    }
  }

  async function fetchAgent(name: string, sessionId?: string | null) {
    const detail = await agentsApi.fetchAgent(name, sessionId)
    detailMap.value[name] = detail
    const index = agents.value.findIndex(agent => agent.name === name)
    if (index >= 0) {
      agents.value[index] = { ...agents.value[index], ...detail }
    } else {
      agents.value = [...agents.value, detail]
    }
    return detail
  }

  async function createAgent(payload: AgentMutationPayload) {
    saving.value = true
    try {
      const created = await agentsApi.createAgent(payload)
      detailMap.value[created.name] = created
      selectedAgentName.value = created.name
      await fetchAgents()
      await fetchAgent(created.name)
      return created
    } finally {
      saving.value = false
    }
  }

  async function updateAgent(name: string, payload: AgentMutationPayload) {
    saving.value = true
    try {
      const updated = await agentsApi.updateAgent(name, payload)
      detailMap.value[name] = updated
      const index = agents.value.findIndex(agent => agent.name === name)
      if (index >= 0) agents.value[index] = { ...agents.value[index], ...updated }
      return updated
    } finally {
      saving.value = false
    }
  }

  async function deleteAgent(name: string) {
    saving.value = true
    try {
      await agentsApi.deleteAgent(name)
      delete detailMap.value[name]
      if (selectedAgentName.value === name) selectedAgentName.value = ''
      await fetchAgents()
      return true
    } finally {
      saving.value = false
    }
  }

  async function activateAgent(name: string, sessionId: string) {
    activating.value = true
    try {
      const res = await agentsApi.activateAgent(name, sessionId)
      activeAgentName.value = res.active_agent_name || 'default'
      agents.value = agents.value.map(agent => ({
        ...agent,
        active: agent.name === activeAgentName.value,
      }))
      return res
    } finally {
      activating.value = false
    }
  }

  return {
    agents,
    detailMap,
    activeAgentName,
    activeAgent,
    selectedAgentName,
    selectedAgent,
    loading,
    saving,
    activating,
    fetchAgents,
    fetchAgent,
    createAgent,
    updateAgent,
    deleteAgent,
    activateAgent,
  }
})
