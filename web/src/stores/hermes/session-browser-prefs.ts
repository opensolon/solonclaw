import { defineStore } from 'pinia'
import { ref } from 'vue'

const PIN_KEY = 'hermes_session_pins_v1'
const HUMAN_ONLY_KEY = 'hermes_human_only_v1'

function loadJson<T>(key: string, fallback: T): T {
  try {
    const raw = localStorage.getItem(key)
    return raw ? JSON.parse(raw) as T : fallback
  } catch {
    return fallback
  }
}

function saveJson(key: string, value: unknown) {
  try {
    localStorage.setItem(key, JSON.stringify(value))
  } catch {
    // ignore quota/storage errors — fall back to in-memory only
  }
}

function sameIds(a: string[], b: string[]): boolean {
  return a.length === b.length && a.every((value, index) => value === b[index])
}

export const useSessionBrowserPrefsStore = defineStore('session-browser-prefs', () => {
  const pinnedIds = ref<string[]>(loadJson<string[]>(PIN_KEY, []))
  const humanOnly = ref<boolean>(loadJson<boolean>(HUMAN_ONLY_KEY, true))

  function reload() {
    pinnedIds.value = loadJson<string[]>(PIN_KEY, [])
    humanOnly.value = loadJson<boolean>(HUMAN_ONLY_KEY, true)
  }

  function persistPins() {
    saveJson(PIN_KEY, pinnedIds.value)
  }

  function persistHumanOnly() {
    saveJson(HUMAN_ONLY_KEY, humanOnly.value)
  }

  function isPinned(sessionId: string): boolean {
    return pinnedIds.value.includes(sessionId)
  }

  function togglePinned(sessionId: string) {
    if (isPinned(sessionId)) {
      pinnedIds.value = pinnedIds.value.filter(id => id !== sessionId)
    } else {
      pinnedIds.value = [...pinnedIds.value, sessionId]
    }
    persistPins()
  }

  function removePinned(sessionId: string): boolean {
    if (!isPinned(sessionId)) return false
    pinnedIds.value = pinnedIds.value.filter(id => id !== sessionId)
    persistPins()
    return true
  }

  function setHumanOnly(value: boolean) {
    if (humanOnly.value === value) return
    humanOnly.value = value
    persistHumanOnly()
  }

  function pruneMissingSessions(existingIds: string[]): boolean {
    if (existingIds.length === 0) return false
    const existing = new Set(existingIds)
    const nextPinnedIds = pinnedIds.value.filter(id => existing.has(id))
    if (sameIds(nextPinnedIds, pinnedIds.value)) return false
    pinnedIds.value = nextPinnedIds
    persistPins()
    return true
  }

  return {
    pinnedIds,
    humanOnly,
    reload,
    isPinned,
    togglePinned,
    removePinned,
    setHumanOnly,
    pruneMissingSessions,
  }
})
