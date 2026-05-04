<script setup lang="ts">
import { useI18n } from 'vue-i18n'
import { useUsageStore } from '@/stores/hermes/usage'

const { t } = useI18n()
const usageStore = useUsageStore()

function formatTokens(n: number): string {
  if (n >= 1000000) return (n / 1000000).toFixed(1) + 'M'
  if (n >= 1000) return (n / 1000).toFixed(1) + 'K'
  return String(n)
}

</script>

<template>
  <div class="stat-cards">
    <div class="stat-card">
      <div class="stat-label">{{ t('usage.totalTokens') }}</div>
      <div class="stat-value">{{ formatTokens(usageStore.totalTokens) }}</div>
      <div class="stat-sub">
        {{ formatTokens(usageStore.totalInputTokens) }} {{ t('usage.inputTokens') }} /
        {{ formatTokens(usageStore.totalOutputTokens) }} {{ t('usage.outputTokens') }}
      </div>
    </div>
    <div class="stat-card">
      <div class="stat-label">{{ t('usage.totalSessions') }}</div>
      <div class="stat-value">{{ usageStore.totalSessions }}</div>
      <div class="stat-sub">{{ t('usage.avgPerDay', { n: usageStore.avgSessionsPerDay.toFixed(1) }) }}</div>
    </div>
    <div class="stat-card">
      <div class="stat-label">{{ t('usage.cacheHitRate') }}</div>
      <div class="stat-value">{{ usageStore.cacheHitRate !== null ? usageStore.cacheHitRate.toFixed(1) + '%' : '--' }}</div>
      <div class="stat-sub" v-if="usageStore.cacheHitRate !== null">
        {{ formatTokens(usageStore.totalCacheReadTokens) }} {{ t('usage.cacheRead') }} /
        {{ formatTokens(usageStore.totalCacheWriteTokens) }} {{ t('usage.cacheWrite') }}
      </div>
    </div>
  </div>
</template>

<style scoped lang="scss">
@use '@/styles/variables' as *;

.stat-cards {
  display: grid;
  grid-template-columns: repeat(3, 1fr);
  gap: 12px;
  margin-bottom: 20px;
}

.stat-card {
  background: $bg-card;
  border: 1px solid $border-color;
  border-radius: $radius-md;
  padding: 16px;
}

.stat-label {
  font-size: 12px;
  color: $text-muted;
  margin-bottom: 6px;
}

.stat-value {
  font-size: 22px;
  font-weight: 600;
  color: $text-primary;
  line-height: 1.2;
}

.stat-sub {
  font-size: 11px;
  color: $text-muted;
  margin-top: 4px;
}

@media (max-width: 768px) {
  .stat-cards {
    grid-template-columns: repeat(2, 1fr);
  }
}

@media (max-width: 480px) {
  .stat-cards {
    grid-template-columns: 1fr;
  }
}
</style>
