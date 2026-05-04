<script setup lang="ts">
import { NInputNumber, NSelect, useMessage } from 'naive-ui'
import { useI18n } from 'vue-i18n'
import { useSettingsStore } from '@/stores/hermes/settings'
import SettingRow from './SettingRow.vue'

const settingsStore = useSettingsStore()
const message = useMessage()
const { t } = useI18n()

async function save(values: Record<string, any>) {
  try {
    await settingsStore.saveSection('agent', values)
    message.success(t('settings.saved'))
  } catch (err: any) {
    message.error(t('settings.saveFailed'))
  }
}
</script>

<template>
  <section class="settings-section">
    <SettingRow :label="t('settings.agent.maxTurns')" :hint="t('settings.agent.maxTurnsHint')">
      <NInputNumber
        :value="settingsStore.agent.max_turns"
        :min="1" :max="200" :step="5"
        size="small" class="input-sm"
        @update:value="v => v != null && save({ max_turns: v })"
      />
    </SettingRow>
    <SettingRow :label="t('settings.agent.gatewayTimeout')" :hint="t('settings.agent.gatewayTimeoutHint')">
      <NInputNumber
        :value="settingsStore.agent.gateway_timeout"
        :min="60" :max="7200" :step="60"
        size="small" class="input-sm"
        @update:value="v => v != null && save({ gateway_timeout: v })"
      />
    </SettingRow>
    <SettingRow :label="t('settings.agent.restartDrainTimeout')" :hint="t('settings.agent.restartDrainTimeoutHint')">
      <NInputNumber
        :value="settingsStore.agent.restart_drain_timeout"
        :min="10" :max="300" :step="10"
        size="small" class="input-sm"
        @update:value="v => v != null && save({ restart_drain_timeout: v })"
      />
    </SettingRow>
    <SettingRow :label="t('settings.agent.toolEnforcement')" :hint="t('settings.agent.toolEnforcementHint')">
      <NSelect
        :value="settingsStore.agent.tool_use_enforcement || 'auto'"
        :options="[
          { label: t('settings.agent.auto'), value: 'auto' },
          { label: t('settings.agent.always'), value: 'always' },
          { label: t('settings.agent.never'), value: 'never' },
        ]"
        size="small" class="input-sm"
        @update:value="v => save({ tool_use_enforcement: v })"
      />
    </SettingRow>
  </section>
</template>

<style scoped lang="scss">
@use '@/styles/variables' as *;

.settings-section {
  margin-top: 16px;
}
</style>
