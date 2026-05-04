<script setup lang="ts">
import { NSwitch, NInputNumber, useMessage } from 'naive-ui'
import { useI18n } from 'vue-i18n'
import { useSettingsStore } from '@/stores/hermes/settings'
import SettingRow from './SettingRow.vue'

const settingsStore = useSettingsStore()
const message = useMessage()
const { t } = useI18n()

async function save(values: Record<string, any>) {
  try {
    await settingsStore.saveSection('memory', values)
    message.success(t('settings.saved'))
  } catch (err: any) {
    message.error(t('settings.saveFailed'))
  }
}
</script>

<template>
  <section class="settings-section">
    <SettingRow :label="t('settings.memory.enabled')" :hint="t('settings.memory.enabledHint')">
      <NSwitch :value="settingsStore.memory.memory_enabled" @update:value="v => save({ memory_enabled: v })" />
    </SettingRow>
    <SettingRow :label="t('settings.memory.userProfile')" :hint="t('settings.memory.userProfileHint')">
      <NSwitch :value="settingsStore.memory.user_profile_enabled" @update:value="v => save({ user_profile_enabled: v })" />
    </SettingRow>
    <SettingRow :label="t('settings.memory.charLimit')" :hint="t('settings.memory.charLimitHint')">
      <NInputNumber
        :value="settingsStore.memory.memory_char_limit"
        :min="100" :max="10000" :step="100"
        size="small" class="input-sm"
        @update:value="v => v != null && save({ memory_char_limit: v })"
      />
    </SettingRow>
    <SettingRow :label="t('settings.memory.userCharLimit')" :hint="t('settings.memory.userCharLimitHint')">
      <NInputNumber
        :value="settingsStore.memory.user_char_limit"
        :min="100" :max="10000" :step="100"
        size="small" class="input-sm"
        @update:value="v => v != null && save({ user_char_limit: v })"
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
