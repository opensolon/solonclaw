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

const maxTokens = computed(() =>
  Math.max(...usageStore.dailyUsage.map(d => d.tokens), 1),
)
</script>

<script lang="ts">
import { computed } from 'vue'
</script>

<template>
  <div class="daily-trend">
    <h3 class="section-title">{{ t('usage.dailyTrend') }}</h3>

    <div class="bar-chart">
      <div
        v-for="d in usageStore.dailyUsage"
        :key="d.date"
        class="bar-col"
      >
        <div class="bar-track">
          <div
            class="bar-fill"
            :style="{ height: (d.tokens / maxTokens * 100) + '%' }"
          />
        </div>
        <div class="bar-tooltip">
          <div class="tooltip-date">{{ d.date }}</div>
          <div class="tooltip-row">{{ t('usage.tokens') }}: {{ formatTokens(d.tokens) }}</div>
          <div class="tooltip-row">{{ t('usage.cacheRead') }}: {{ formatTokens(d.cacheRead) }}</div>
          <div class="tooltip-row">{{ t('usage.cacheWrite') }}: {{ formatTokens(d.cacheWrite) }}</div>
          <div class="tooltip-row">{{ t('usage.sessions') }}: {{ d.sessions }}</div>
        </div>
      </div>
    </div>
    <div class="bar-dates">
      <span>{{ usageStore.dailyUsage[0]?.date.slice(5) }}</span>
      <span>{{ usageStore.dailyUsage[usageStore.dailyUsage.length - 1]?.date.slice(5) }}</span>
    </div>

    <div class="trend-table">
      <table>
        <thead>
          <tr>
            <th>{{ t('usage.date') }}</th>
            <th>{{ t('usage.tokens') }}</th>
            <th>{{ t('usage.cacheRead') }}</th>
            <th>{{ t('usage.cacheWrite') }}</th>
            <th>{{ t('usage.sessions') }}</th>
          </tr>
        </thead>
        <tbody>
          <tr v-for="d in [...usageStore.dailyUsage].reverse().slice(0, 30)" :key="d.date">
            <td>{{ d.date }}</td>
            <td>{{ formatTokens(d.tokens) }}</td>
            <td>{{ formatTokens(d.cacheRead) }}</td>
            <td>{{ formatTokens(d.cacheWrite) }}</td>
            <td>{{ d.sessions }}</td>
          </tr>
        </tbody>
      </table>
    </div>
  </div>
</template>

<style scoped lang="scss">
@use '@/styles/variables' as *;

.daily-trend {
  background: $bg-card;
  border: 1px solid $border-color;
  border-radius: $radius-md;
  padding: 16px;
}

.section-title {
  font-size: 13px;
  font-weight: 600;
  color: $text-secondary;
  margin: 0 0 12px;
}

.bar-chart {
  display: flex;
  gap: 2px;
  margin-bottom: 16px;
}

.bar-col {
  flex: 1;
  min-width: 0;
  display: flex;
  flex-direction: column;
  align-items: center;
}

.bar-track {
  width: 100%;
  height: 140px;
  background: $bg-secondary;
  border-radius: 2px 2px 0 0;
  display: flex;
  align-items: flex-end;
}

.bar-fill {
  width: 100%;
  background: $text-primary;
  border-radius: 2px 2px 0 0;
  min-height: 0;
  transition: height 0.3s ease;

  .dark & {
    background: #66bb6a;
  }
}

.bar-col {
  position: relative;
}

.bar-tooltip {
  display: none;
  position: absolute;
  bottom: calc(100% + 8px);
  left: 50%;
  transform: translateX(-50%);
  background: $text-primary;
  color: var(--text-on-accent);
  padding: 6px 10px;
  border-radius: $radius-sm;
  font-size: 11px;
  white-space: nowrap;
  z-index: 10;
  pointer-events: none;

  &::after {
    content: '';
    position: absolute;
    top: 100%;
    left: 50%;
    transform: translateX(-50%);
    border: 5px solid transparent;
    border-top-color: $text-primary;
  }
}

.bar-col:hover .bar-tooltip {
  display: block;
}

.tooltip-date {
  font-weight: 600;
  margin-bottom: 2px;
}

.tooltip-row {
  font-size: 10px;
  opacity: 0.85;
  line-height: 1.5;
}

.bar-label {
  display: none;
}

.bar-dates {
  display: flex;
  justify-content: space-between;
  font-size: 10px;
  color: $text-muted;
  margin-top: 4px;
  margin-bottom: 16px;
}

.trend-table {
  overflow-x: auto;
}

table {
  width: 100%;
  border-collapse: collapse;
  font-size: 12px;
}

thead {
  position: sticky;
  top: 0;
}

th {
  text-align: left;
  padding: 8px 10px;
  font-weight: 600;
  color: $text-muted;
  border-bottom: 1px solid $border-color;
  background: $bg-card;
  font-size: 11px;
  text-transform: uppercase;
  letter-spacing: 0.3px;
}

td {
  padding: 6px 10px;
  color: $text-secondary;
  border-bottom: 1px solid $border-light;
  font-family: $font-code;
  font-size: 11px;
}

tr:last-child td {
  border-bottom: none;
}
</style>
