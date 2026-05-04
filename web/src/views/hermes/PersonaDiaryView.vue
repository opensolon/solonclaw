<script setup lang="ts">
import { ref, onMounted, onUnmounted } from 'vue'
import MarkdownRenderer from '@/components/hermes/chat/MarkdownRenderer.vue'
import { fetchPersonaDiaries, fetchPersonaDiary, type PersonaDiaryEntry } from '@/api/hermes/persona'

const diaries = ref<PersonaDiaryEntry[]>([])
const loading = ref(false)
const selectedPath = ref('')
const content = ref('')
const showSidebar = ref(true)
let mobileQuery: MediaQueryList | null = null

function handleMobileChange(e: MediaQueryListEvent | MediaQueryList) {
  showSidebar.value = !e.matches
}

async function loadDiaryList() {
  loading.value = true
  try {
    diaries.value = await fetchPersonaDiaries()
    if (!selectedPath.value && diaries.value.length > 0) {
      await selectDiary(diaries.value[0].relativePath)
    }
  } finally {
    loading.value = false
  }
}

async function selectDiary(path: string) {
  selectedPath.value = path
  const diary = await fetchPersonaDiary(path)
  content.value = diary.content || ''
  if (window.innerWidth <= 768) {
    showSidebar.value = false
  }
}

onMounted(() => {
  mobileQuery = window.matchMedia('(max-width: 768px)')
  handleMobileChange(mobileQuery)
  mobileQuery.addEventListener('change', handleMobileChange)
  loadDiaryList()
})

onUnmounted(() => {
  mobileQuery?.removeEventListener('change', handleMobileChange)
})
</script>

<template>
  <div class="skills-view">
    <header class="page-header">
      <div style="display: flex; align-items: center; gap: 8px;">
        <h2 class="header-title">日记</h2>
        <button v-if="!showSidebar" class="sidebar-toggle" @click="showSidebar = true">
          <svg width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><line x1="3" y1="12" x2="21" y2="12"/><line x1="3" y1="6" x2="21" y2="6"/><line x1="3" y1="18" x2="21" y2="18"/></svg>
        </button>
      </div>
    </header>

    <div class="skills-content">
      <div v-if="loading && diaries.length === 0" class="skills-loading">加载中...</div>
      <div v-else class="skills-layout">
        <div class="mobile-backdrop" :class="{ active: showSidebar }" @click="showSidebar = false" />
        <div v-if="showSidebar" class="skills-sidebar">
          <div class="diary-list">
            <button
              v-for="diary in diaries"
              :key="diary.relativePath"
              class="diary-item"
              :class="{ active: selectedPath === diary.relativePath }"
              @click="selectDiary(diary.relativePath)"
            >
              {{ diary.name.replace('.md', '') }}
            </button>
          </div>
        </div>
        <div class="skills-main">
          <div v-if="selectedPath" class="diary-detail">
            <div class="detail-title">{{ selectedPath.split('/').pop()?.replace('.md', '') }}</div>
            <div class="detail-content">
              <MarkdownRenderer v-if="content.trim()" :content="content" />
              <div v-else class="empty-detail">暂无内容</div>
            </div>
          </div>
          <div v-else class="empty-detail">暂无日记</div>
        </div>
      </div>
    </div>
  </div>
</template>

<style scoped lang="scss">
@use '@/styles/variables' as *;

.skills-view {
  height: calc(100 * var(--vh));
  display: flex;
  flex-direction: column;
}

.skills-content {
  flex: 1;
  overflow: hidden;
}

.skills-loading {
  display: flex;
  align-items: center;
  justify-content: center;
  height: 100%;
  font-size: 13px;
  color: $text-muted;
}

.skills-layout {
  display: flex;
  height: 100%;
}

.skills-sidebar {
  width: 280px;
  border-right: 1px solid $border-color;
  flex-shrink: 0;
  display: flex;
  flex-direction: column;
  overflow: hidden;
  min-height: 0;
}

.diary-list {
  flex: 1;
  overflow-y: auto;
  padding: 8px;
}

.diary-item {
  display: flex;
  width: 100%;
  padding: 8px 10px;
  border: none;
  background: none;
  color: $text-secondary;
  font-size: 13px;
  text-align: left;
  cursor: pointer;
  border-radius: $radius-sm;
  transition: all $transition-fast;

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

.skills-main {
  flex: 1;
  overflow-y: auto;
  padding: 16px 20px;
  min-width: 0;
}

.detail-title {
  padding-bottom: 12px;
  border-bottom: 1px solid $border-color;
  margin-bottom: 12px;
  font-size: 15px;
  font-weight: 600;
  color: $text-primary;
}

.detail-content {
  min-height: 0;
}

.sidebar-toggle {
  display: none;
  border: none;
  background: none;
  cursor: pointer;
  color: $text-secondary;
  padding: 4px;
  border-radius: $radius-sm;

  &:hover {
    background: rgba(var(--accent-primary-rgb), 0.06);
  }
}

.empty-detail {
  height: 100%;
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  gap: 12px;
  color: $text-muted;
  font-size: 13px;
}

@media (max-width: $breakpoint-mobile) {
  .sidebar-toggle {
    display: flex;
  }

  .skills-sidebar {
    position: absolute;
    left: 0;
    top: 0;
    height: 100%;
    z-index: 10;
    background: $bg-card;
    box-shadow: 2px 0 8px rgba(0, 0, 0, 0.1);
  }

  .skills-layout {
    position: relative;
  }

  .mobile-backdrop {
    display: block;
    position: absolute;
    inset: 0;
    background: rgba(0, 0, 0, 0.4);
    z-index: 9;
    opacity: 0;
    pointer-events: none;
    transition: opacity $transition-fast;

    &.active {
      opacity: 1;
      pointer-events: auto;
    }
  }
}
</style>
