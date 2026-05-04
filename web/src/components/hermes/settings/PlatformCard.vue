<script setup lang="ts">
import { ref, computed } from 'vue'
import { NTag } from 'naive-ui'
import { useI18n } from 'vue-i18n'

const props = defineProps<{
  name: string
  icon: string
  config: Record<string, any>
  credentials?: Record<string, any>
}>()

const expanded = ref(true)
const { t } = useI18n()

const enabled = computed(() => {
  if (props.credentials?.enabled !== undefined) return !!props.credentials.enabled
  return !!props.config?.enabled
})
</script>

<template>
  <div class="platform-card" :class="{ enabled }">
    <div class="platform-card-header" @click="expanded = !expanded">
      <div class="platform-info">
        <span class="platform-icon" v-html="icon" />
        <span class="platform-name">{{ name }}</span>
        <NTag :type="enabled ? 'success' : 'default'" size="small" round>
          {{ enabled ? t('common.enabled') : t('common.closed') }}
        </NTag>
      </div>
      <span class="expand-icon" :class="{ expanded }">&#9662;</span>
    </div>
    <div v-if="expanded" class="platform-card-body">
      <slot />
    </div>
  </div>
</template>

<style scoped lang="scss">
@use '@/styles/variables' as *;

.platform-card {
  background-color: $bg-card;
  border: 1px solid $border-color;
  border-radius: $radius-md;
  margin-bottom: 12px;
  overflow: hidden;

  &.enabled {
    border-color: rgba(var(--success-rgb), 0.2);
  }
}

.platform-card-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 12px 16px;
  cursor: pointer;
  user-select: none;

  &:hover {
    background-color: rgba(var(--text-primary-rgb), 0.03);
  }
}

.platform-info {
  display: flex;
  align-items: center;
  gap: 10px;
}

.platform-icon {
  width: 18px;
  height: 18px;
  color: $text-secondary;
  flex-shrink: 0;
}

.platform-name {
  font-size: 14px;
  font-weight: 500;
  color: $text-primary;
}

.expand-icon {
  font-size: 12px;
  color: $text-muted;
  transition: transform 0.2s;

  &.expanded {
    transform: rotate(0deg);
  }

  &:not(.expanded) {
    transform: rotate(-90deg);
  }
}

.platform-card-body {
  padding: 0 16px 12px;
  border-top: 1px solid $border-light;
}
</style>
