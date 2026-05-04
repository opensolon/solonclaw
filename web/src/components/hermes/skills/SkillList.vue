<script setup lang="ts">
import { ref, computed } from 'vue'
import { NSwitch, useMessage } from 'naive-ui'
import type { SkillCategory } from '@/api/hermes/skills'
import { toggleSkill } from '@/api/hermes/skills'
import { useI18n } from 'vue-i18n'

const { t } = useI18n()
const message = useMessage()

const props = defineProps<{
  categories: SkillCategory[]
  selectedSkill: string | null
  searchQuery: string
}>()

const emit = defineEmits<{
  select: [category: string, skill: string]
}>()

const collapsedCategories = ref<Set<string>>(new Set())
const togglingSkills = ref<Set<string>>(new Set())

const filteredCategories = computed(() => {
  if (!props.searchQuery) return props.categories
  const q = props.searchQuery.toLowerCase()
  return props.categories
    .map(cat => ({
      ...cat,
      skills: cat.skills.filter(
        s => s.name.toLowerCase().includes(q) || s.description.toLowerCase().includes(q),
      ),
    }))
    .filter(cat => cat.skills.length > 0 || cat.name.toLowerCase().includes(q))
})

function toggleCategory(name: string) {
  if (collapsedCategories.value.has(name)) {
    collapsedCategories.value.delete(name)
  } else {
    collapsedCategories.value.add(name)
  }
}

function handleSelect(category: string, skill: string) {
  emit('select', category, skill)
}

async function handleToggle(category: string, skillName: string, newEnabled: boolean) {
  if (togglingSkills.value.has(skillName)) return
  togglingSkills.value.add(skillName)

  try {
    await toggleSkill(category, skillName, newEnabled)
    // Update local state
    const cat = props.categories.find(c => c.name === category)
    const skill = cat?.skills.find(s => s.name === skillName)
    if (skill) skill.enabled = newEnabled
  } catch (err: any) {
    message.error(t('skills.toggleFailed') + `: ${err.message}`)
  } finally {
    togglingSkills.value.delete(skillName)
  }
}
</script>

<template>
  <div class="skill-list">
    <div v-if="filteredCategories.length === 0" class="skill-empty">
      {{ searchQuery ? t('skills.noMatch') : t('skills.noSkills') }}
    </div>
    <div
      v-for="cat in filteredCategories"
      :key="cat.name"
      class="skill-category"
    >
      <button class="category-header" @click="toggleCategory(cat.name)">
        <svg
          width="12" height="12" viewBox="0 0 24 24" fill="none"
          stroke="currentColor" stroke-width="2"
          class="category-arrow"
          :class="{ collapsed: collapsedCategories.has(cat.name) }"
        >
          <polyline points="6 9 12 15 18 9" />
        </svg>
        <span class="category-name">{{ cat.name }}</span>
        <span class="category-count">{{ cat.skills.length }}</span>
      </button>
      <div v-if="!collapsedCategories.has(cat.name)" class="category-skills">
        <button
          v-for="skill in cat.skills"
          :key="skill.name"
          class="skill-item"
          :class="{
            active: selectedSkill === `${cat.name}/${skill.name}`,
          }"
          @click="handleSelect(cat.name, skill.name)"
        >
          <div class="skill-info">
            <span class="skill-name">{{ skill.name }}</span>
            <span v-if="skill.description" class="skill-desc">{{ skill.description }}</span>
          </div>
          <NSwitch
            size="small"
            :value="skill.enabled !== false"
            :loading="togglingSkills.has(skill.name)"
            @update:value="handleToggle(cat.name, skill.name, $event)"
            @click.stop
          />
        </button>
      </div>
    </div>
  </div>
</template>

<style scoped lang="scss">
@use '@/styles/variables' as *;

.skill-list {
  flex: 1;
  overflow-y: auto;
  padding: 8px;
}

.skill-empty {
  padding: 24px 16px;
  font-size: 13px;
  color: $text-muted;
  text-align: center;
}

.skill-category {
  margin-bottom: 4px;
}

.category-header {
  display: flex;
  align-items: center;
  gap: 6px;
  width: 100%;
  padding: 6px 10px;
  border: none;
  background: none;
  color: $text-secondary;
  font-size: 12px;
  font-weight: 600;
  text-transform: uppercase;
  letter-spacing: 0.3px;
  cursor: pointer;
  border-radius: $radius-sm;

  &:hover {
    background: rgba(var(--accent-primary-rgb), 0.04);
  }
}

.category-arrow {
  flex-shrink: 0;
  transition: transform $transition-fast;

  &.collapsed {
    transform: rotate(-90deg);
  }
}

.category-name {
  flex: 1;
  text-align: left;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.category-count {
  font-size: 11px;
  color: $text-muted;
  background: rgba(var(--accent-primary-rgb), 0.06);
  padding: 1px 6px;
  border-radius: 8px;
}

.category-skills {
  padding: 2px 0 4px;
}

.skill-item {
  display: flex;
  flex-direction: row;
  align-items: center;
  width: 100%;
  padding: 6px 10px 6px 28px;
  border: none;
  background: none;
  color: $text-secondary;
  font-size: 13px;
  text-align: left;
  cursor: pointer;
  border-radius: $radius-sm;
  transition: all $transition-fast;
  gap: 8px;

  &:hover {
    background: rgba(var(--accent-primary-rgb), 0.06);
    color: $text-primary;
  }

  &.active {
    background: rgba(var(--accent-primary-rgb), 0.1);
    color: $text-primary;
    font-weight: 500;
  }
}

.skill-info {
  flex: 1;
  min-width: 0;
  display: flex;
  flex-direction: column;
}

.skill-name {
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
}

.skill-desc {
  font-size: 11px;
  color: $text-muted;
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
  margin-top: 1px;
}
</style>
