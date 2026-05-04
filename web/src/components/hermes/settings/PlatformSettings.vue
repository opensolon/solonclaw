<script setup lang="ts">
import { ref, reactive, onUnmounted } from 'vue'
import * as QRCode from 'qrcode'
import { NSwitch, NInput, NButton, NSpin, useMessage } from 'naive-ui'
import { useI18n } from 'vue-i18n'
import { useSettingsStore } from '@/stores/hermes/settings'
import { saveCredentials as saveCredsApi, fetchWeixinQrCode, pollWeixinQrStatus } from '@/api/hermes/config'
import PlatformCard from './PlatformCard.vue'
import SettingRow from './SettingRow.vue'

const settingsStore = useSettingsStore()
const message = useMessage()
const { t } = useI18n()

// Track saving state per platform.field
const saving = reactive<Record<string, boolean>>({})

function savingKey(platform: string, field: string) {
  return `${platform}.${field}`
}

function isSaving(platform: string, field: string) {
  return !!saving[savingKey(platform, field)]
}

// Immediate save for switches
async function immediateSave(platform: string, field: string, saveFn: () => Promise<void>) {
  const key = savingKey(platform, field)
  saving[key] = true
  try {
    await saveFn()
    message.success(t('settings.saved'))
  } catch (err: any) {
    message.error(t('settings.saveFailed'))
  } finally {
    saving[key] = false
  }
}

async function saveChannel(platform: string, field: string, values: Record<string, any>) {
  immediateSave(platform, field, () => settingsStore.saveSection(platform, values))
}

// Save credentials to runtime/config.yml through the dashboard API
async function saveCredentials(platform: string, field: string, values: Record<string, any>) {
  immediateSave(platform, field, async () => {
    await saveCredsApi(platform, values)
    await settingsStore.fetchSettings()
  })
}

function getCreds(key: string) {
  return (settingsStore.platforms[key] || {}) as Record<string, any>
}

// Weixin QR code login state
const wxQrUrl = ref('')
const wxQrImageUrl = ref('')
const wxQrMessage = ref('')
const wxQrId = ref('')
const wxQrStatus = ref<'idle' | 'loading' | 'waiting' | 'scaned' | 'confirmed' | 'error' | 'expired'>('idle')
let wxPollTimer: ReturnType<typeof setTimeout> | null = null

async function updateWeixinQrSource(raw: string) {
  const value = (raw || '').trim()
  if (!value || value === wxQrUrl.value) return
  wxQrUrl.value = value
  wxQrImageUrl.value = /^data:image\//i.test(value)
    ? value
    : await QRCode.toDataURL(value, {
      width: 240,
      margin: 2,
      errorCorrectionLevel: 'M',
    })
}

async function startWeixinQrLogin() {
  wxQrStatus.value = 'loading'
  wxQrUrl.value = ''
  wxQrImageUrl.value = ''
  wxQrMessage.value = ''
  wxQrId.value = ''
  stopWeixinPoll()

  try {
    const data = await fetchWeixinQrCode()
    wxQrId.value = data.qrcode
    await updateWeixinQrSource(data.qrcode_url)
    wxQrStatus.value = wxQrUrl.value ? 'waiting' : 'loading'
    pollWeixinStatus()
  } catch (err: any) {
    wxQrStatus.value = 'error'
    wxQrMessage.value = err.message || t('platform.qrFetching')
    message.error(err.message || t('platform.qrFetching'))
  }
}

function pollWeixinStatus() {
  if (!wxQrId.value) return
  wxPollTimer = setTimeout(async () => {
    try {
      const data = await pollWeixinQrStatus(wxQrId.value)
      wxQrMessage.value = data.error_message || data.message || ''
      await updateWeixinQrSource(data.qrcode_url || '')
      if (data.status === 'wait') {
        wxQrStatus.value = wxQrUrl.value ? 'waiting' : 'loading'
        pollWeixinStatus()
      } else if (data.status === 'scaned') {
        wxQrStatus.value = 'scaned'
        pollWeixinStatus()
      } else if (data.status === 'expired') {
        wxQrStatus.value = 'expired'
      } else if (data.status === 'error') {
        wxQrStatus.value = 'error'
      } else if (data.status === 'confirmed') {
        wxQrStatus.value = 'confirmed'
        await settingsStore.fetchSettings()
        message.success(t('settings.saved'))
      }
    } catch {
      pollWeixinStatus()
    }
  }, 3000)
}

function stopWeixinPoll() {
  if (wxPollTimer) {
    clearTimeout(wxPollTimer)
    wxPollTimer = null
  }
}

onUnmounted(() => {
  stopWeixinPoll()
})

const platforms = [
  {
    key: 'feishu',
    name: '飞书',
    icon: '<svg viewBox="0 0 24 24" fill="currentColor"><path d="M6.59 3.41a2.25 2.25 0 0 1 3.182 0L13.5 7.14l-3.182 3.182L6.59 7.59a2.25 2.25 0 0 1 0-3.182zm5.303 5.303L15.075 5.53a2.25 2.25 0 0 1 3.182 3.182L15.075 11.894 11.893 8.713zM3.41 6.59a2.25 2.25 0 0 1 3.182 0l3.182 3.182-3.182 3.182a2.25 2.25 0 0 1-3.182-3.182L3.41 6.59zm5.303 5.303L11.894 15.075a2.25 2.25 0 0 1-3.182 3.182L5.53 15.075 8.713 11.893zm5.303-5.303L17.478 9.778a2.25 2.25 0 0 1-3.182 3.182L10.53 10.075l3.182-3.182 0 .023z"/></svg>',
  },
  {
    key: 'dingtalk',
    name: '钉钉',
    icon: '<svg viewBox="0 0 24 24" fill="currentColor"><path d="M18.53 3.47c-1.71-1.71-4.87-2.18-8.02-.98-2.35.9-4.4 2.72-5.58 4.88-.74 1.37-1.02 2.74-.83 4.01.21 1.47.95 2.61 2.12 3.28 1.36.78 3.22.86 5.22.19-.45.72-1.19 1.38-2.14 1.92-1.14.65-2.38.96-3.4.9 1.12 1.03 2.72 1.54 4.52 1.38 1.83-.17 3.63-1.03 5.08-2.39 1.75-1.63 2.8-3.8 2.96-5.97.09-1.19-.11-2.25-.57-3.11.92.18 1.74.62 2.31 1.29.14-1.98-.44-3.83-1.67-5.06zm-7.7 8.96c-.86 0-1.56-.7-1.56-1.56s.7-1.56 1.56-1.56 1.56.7 1.56 1.56-.7 1.56-1.56 1.56z"/></svg>',
  },
  {
    key: 'weixin',
    name: '微信',
    icon: '<svg viewBox="0 0 24 24" fill="currentColor"><path d="M8.691 2.188C3.891 2.188 0 5.476 0 9.53c0 2.212 1.17 4.203 3.002 5.55a.59.59 0 01.213.665l-.39 1.48c-.019.07-.048.141-.048.213 0 .163.13.295.29.295a.326.326 0 00.167-.054l1.903-1.114a.864.864 0 01.717-.098 10.16 10.16 0 002.837.403c.276 0 .543-.027.811-.05-.857-2.578.157-4.972 1.932-6.446 1.703-1.415 3.882-1.98 5.853-1.838-.576-3.583-4.196-6.348-8.596-6.348zM5.785 5.991c.642 0 1.162.529 1.162 1.18a1.17 1.17 0 01-1.162 1.178A1.17 1.17 0 014.623 7.17c0-.651.52-1.18 1.162-1.18zm5.813 0c.642 0 1.162.529 1.162 1.18a1.17 1.17 0 01-1.162 1.178 1.17 1.17 0 01-1.162-1.178c0-.651.52-1.18 1.162-1.18zm3.68 4.025c-3.694 0-6.69 2.462-6.69 5.496 0 3.034 2.996 5.496 6.69 5.496.753 0 1.477-.1 2.158-.28a.66.66 0 01.548.074l1.46.854a.25.25 0 00.127.041.224.224 0 00.221-.225c0-.055-.022-.109-.037-.162l-.298-1.131a.453.453 0 01.163-.509C21.81 18.613 22.77 16.973 22.77 15.512c0-3.034-2.996-5.496-6.69-5.496h.198zm-2.454 3.347c.491 0 .889.404.889.902a.896.896 0 01-.889.903.896.896 0 01-.889-.903c0-.498.398-.902.889-.902zm4.912 0c.491 0 .889.404.889.902a.896.896 0 01-.889.903.896.896 0 01-.889-.903c0-.498.398-.902.889-.902z"/></svg>',
  },
  {
    key: 'wecom',
    name: '企业微信',
    icon: '<svg viewBox="0 0 24 24" fill="currentColor"><path d="M8.691 2.188C3.891 2.188 0 5.476 0 9.53c0 2.212 1.17 4.203 3.002 5.55a.59.59 0 01.213.665l-.39 1.48c-.019.07-.048.141-.048.213 0 .163.13.295.29.295a.326.326 0 00.167-.054l1.903-1.114a.864.864 0 01.717-.098 10.16 10.16 0 002.837.403c.276 0 .543-.027.811-.05-.857-2.578.157-4.972 1.932-6.446 1.703-1.415 3.882-1.98 5.853-1.838-.576-3.583-4.196-6.348-8.596-6.348zM5.785 5.991c.642 0 1.162.529 1.162 1.18a1.17 1.17 0 01-1.162 1.178A1.17 1.17 0 014.623 7.17c0-.651.52-1.18 1.162-1.18zm5.813 0c.642 0 1.162.529 1.162 1.18a1.17 1.17 0 01-1.162 1.178 1.17 1.17 0 01-1.162-1.178c0-.651.52-1.18 1.162-1.18zm3.68 4.025c-3.694 0-6.69 2.462-6.69 5.496 0 3.034 2.996 5.496 6.69 5.496.753 0 1.477-.1 2.158-.28a.66.66 0 01.548.074l1.46.854a.25.25 0 00.127.041.224.224 0 00.221-.225c0-.055-.022-.109-.037-.162l-.298-1.131a.453.453 0 01.163-.509C21.81 18.613 22.77 16.973 22.77 15.512c0-3.034-2.996-5.496-6.69-5.496h.198zm-2.454 3.347c.491 0 .889.404.889.902a.896.896 0 01-.889.903.896.896 0 01-.889-.903c0-.498.398-.902.889-.902zm4.912 0c.491 0 .889.404.889.902a.896.896 0 01-.889.903.896.896 0 01-.889-.903c0-.498.398-.902.889-.902z"/></svg>',
  },
]
</script>

<template>
  <section class="settings-section">
    <PlatformCard
      v-for="p in platforms"
      :key="p.key"
      :name="p.name"
      :icon="p.icon"
      :config="settingsStore[p.key as keyof typeof settingsStore] as Record<string, any>"
      :credentials="getCreds(p.key)"
    >
      <!-- 飞书 -->
      <template v-if="p.key === 'feishu'">
        <SettingRow :label="t('platform.channelEnabled')" :hint="t('platform.channelEnabledHint')">
          <NSwitch :value="getCreds('feishu').enabled" :loading="isSaving('feishu', 'enabled')" @update:value="v => saveCredentials('feishu', 'enabled', { enabled: v })" />
        </SettingRow>
        <SettingRow :label="t('platform.appId')" :hint="t('platform.appIdHint')">
          <NInput :default-value="getCreds('feishu').extra?.app_id || ''" :loading="isSaving('feishu', 'app_id')" clearable size="small" class="input-lg" placeholder="请输入飞书应用 ID" @change="v => saveCredentials('feishu', 'app_id', { extra: { ...getCreds('feishu').extra, app_id: v } })" />
        </SettingRow>
        <SettingRow :label="t('platform.appSecret')" :hint="t('platform.appSecretHint')">
          <NInput :default-value="getCreds('feishu').extra?.app_secret || ''" :loading="isSaving('feishu', 'app_secret')" clearable size="small" class="input-lg" placeholder="请输入应用密钥" @change="v => saveCredentials('feishu', 'app_secret', { extra: { ...getCreds('feishu').extra, app_secret: v } })" />
        </SettingRow>
        <SettingRow :label="t('platform.requireMention')" :hint="t('platform.requireMentionGroup')">
          <NSwitch :value="settingsStore.feishu.require_mention" :loading="isSaving('feishu', 'require_mention')" @update:value="v => saveChannel('feishu', 'require_mention', { require_mention: v })" />
        </SettingRow>
        <SettingRow :label="t('platform.freeResponseChats')" :hint="t('platform.freeResponseChatsHint')">
          <NInput :default-value="settingsStore.feishu.free_response_chats || ''" :loading="isSaving('feishu', 'free_response_chats')" size="small" placeholder="chat_id1,chat_id2" @change="v => saveChannel('feishu', 'free_response_chats', { free_response_chats: v })" />
        </SettingRow>
      </template>

      <!-- 钉钉 -->
      <template v-if="p.key === 'dingtalk'">
        <SettingRow :label="t('platform.channelEnabled')" :hint="t('platform.channelEnabledHint')">
          <NSwitch :value="getCreds('dingtalk').enabled" :loading="isSaving('dingtalk', 'enabled')" @update:value="v => saveCredentials('dingtalk', 'enabled', { enabled: v })" />
        </SettingRow>
        <SettingRow :label="t('platform.clientId')" :hint="t('platform.clientIdHint')">
          <NInput :default-value="getCreds('dingtalk').extra?.client_id || ''" :loading="isSaving('dingtalk', 'client_id')" clearable size="small" class="input-lg" placeholder="请输入客户端 ID" @change="v => saveCredentials('dingtalk', 'client_id', { extra: { ...getCreds('dingtalk').extra, client_id: v } })" />
        </SettingRow>
        <SettingRow :label="t('platform.clientSecret')" :hint="t('platform.clientSecretHint')">
          <NInput :default-value="getCreds('dingtalk').extra?.client_secret || ''" :loading="isSaving('dingtalk', 'client_secret')" clearable size="small" class="input-lg" placeholder="请输入客户端密钥" @change="v => saveCredentials('dingtalk', 'client_secret', { extra: { ...getCreds('dingtalk').extra, client_secret: v } })" />
        </SettingRow>
        <SettingRow label="机器人编码" hint="钉钉机器人编码">
          <NInput :default-value="getCreds('dingtalk').extra?.robot_code || ''" :loading="isSaving('dingtalk', 'robot_code')" clearable size="small" class="input-lg" placeholder="请输入机器人编码" @change="v => saveCredentials('dingtalk', 'robot_code', { extra: { ...getCreds('dingtalk').extra, robot_code: v } })" />
        </SettingRow>
        <SettingRow :label="t('platform.requireMention')" :hint="t('platform.requireMentionGroup')">
          <NSwitch :value="settingsStore.dingtalk.require_mention" :loading="isSaving('dingtalk', 'require_mention')" @update:value="v => saveChannel('dingtalk', 'require_mention', { require_mention: v })" />
        </SettingRow>
        <SettingRow :label="t('platform.freeResponseChats')" :hint="t('platform.freeResponseChatsHint')">
          <NInput :default-value="settingsStore.dingtalk.free_response_chats || ''" :loading="isSaving('dingtalk', 'free_response_chats')" size="small" placeholder="chat_id1,chat_id2" @change="v => saveChannel('dingtalk', 'free_response_chats', { free_response_chats: v })" />
        </SettingRow>
      </template>

      <!-- 微信 -->
      <template v-if="p.key === 'weixin'">
        <SettingRow :label="t('platform.channelEnabled')" :hint="t('platform.channelEnabledHint')">
          <NSwitch :value="getCreds('weixin').enabled" :loading="isSaving('weixin', 'enabled')" @update:value="v => saveCredentials('weixin', 'enabled', { enabled: v })" />
        </SettingRow>
        <div class="weixin-qr-section">
          <NButton
            v-if="wxQrStatus === 'idle' || wxQrStatus === 'error' || wxQrStatus === 'expired' || wxQrStatus === 'confirmed'"
            type="primary"
            size="small"
            @click="startWeixinQrLogin"
          >
            {{ wxQrStatus === 'confirmed' ? t('platform.qrRelogin') : t('platform.qrLogin') }}
          </NButton>
          <div v-if="wxQrStatus === 'loading'" class="weixin-qr-loading">
            <NSpin size="small" />
            <span>{{ t('platform.qrFetching') }}</span>
          </div>
          <div v-if="wxQrImageUrl" class="weixin-qr-panel">
            <img class="weixin-qr-image" :src="wxQrImageUrl" :alt="t('platform.qrLogin')" />
            <div class="weixin-qr-caption">
              {{ wxQrStatus === 'scaned' ? t('platform.qrScanedHint') : t('platform.qrScanHint') }}
            </div>
            <a
              v-if="/^https?:\/\//i.test(wxQrUrl)"
              class="weixin-qr-link"
              :href="wxQrUrl"
              target="_blank"
              rel="noopener noreferrer"
            >
              {{ wxQrUrl }}
            </a>
          </div>
          <div v-if="!wxQrImageUrl && (wxQrStatus === 'waiting' || wxQrStatus === 'scaned')" class="weixin-qr-hint">
            {{ wxQrStatus === 'scaned' ? t('platform.qrScanedHint') : t('platform.qrScanHint') }}
          </div>
          <div v-if="wxQrStatus === 'error' || wxQrStatus === 'expired'" class="weixin-qr-error">
            {{ wxQrMessage || (wxQrStatus === 'expired' ? t('platform.qrExpired') : t('platform.qrFailed')) }}
          </div>
        </div>
        <SettingRow :label="t('platform.weixinToken')" :hint="t('platform.weixinTokenHint')">
          <NInput :default-value="getCreds('weixin').token || ''" :loading="isSaving('weixin', 'token')" clearable size="small" class="input-lg" placeholder="请输入令牌" @change="v => saveCredentials('weixin', 'token', { token: v })" />
        </SettingRow>
        <SettingRow :label="t('platform.accountId')" :hint="t('platform.accountIdHint')">
          <NInput :default-value="getCreds('weixin').extra?.account_id || ''" :loading="isSaving('weixin', 'account_id')" clearable size="small" class="input-lg" placeholder="请输入账号 ID" @change="v => saveCredentials('weixin', 'account_id', { extra: { ...getCreds('weixin').extra, account_id: v } })" />
        </SettingRow>
      </template>

      <!-- 企业微信 -->
      <template v-if="p.key === 'wecom'">
        <SettingRow :label="t('platform.channelEnabled')" :hint="t('platform.channelEnabledHint')">
          <NSwitch :value="getCreds('wecom').enabled" :loading="isSaving('wecom', 'enabled')" @update:value="v => saveCredentials('wecom', 'enabled', { enabled: v })" />
        </SettingRow>
        <SettingRow :label="t('platform.botId')" :hint="t('platform.botIdHint')">
          <NInput :default-value="getCreds('wecom').extra?.bot_id || ''" :loading="isSaving('wecom', 'bot_id')" clearable size="small" class="input-lg" placeholder="请输入机器人 ID" @change="v => saveCredentials('wecom', 'bot_id', { extra: { ...getCreds('wecom').extra, bot_id: v } })" />
        </SettingRow>
        <SettingRow :label="t('platform.appSecret')" :hint="t('platform.wecomSecretHint')">
          <NInput :default-value="getCreds('wecom').extra?.secret || ''" :loading="isSaving('wecom', 'secret')" clearable size="small" class="input-lg" placeholder="请输入密钥" @change="v => saveCredentials('wecom', 'secret', { extra: { ...getCreds('wecom').extra, secret: v } })" />
        </SettingRow>
      </template>
    </PlatformCard>
  </section>
</template>

<style scoped lang="scss">
@use '@/styles/variables' as *;

.settings-section {
  margin-top: 16px;
}

.weixin-qr-section {
  margin-top: 12px;
  margin-bottom: 12px;
}

.weixin-qr-loading {
  display: flex;
  align-items: center;
  gap: 8px;
  color: $text-muted;
  font-size: 13px;
}

.weixin-qr-panel {
  display: flex;
  flex-direction: column;
  align-items: flex-start;
  gap: 8px;
  margin-top: 10px;
}

.weixin-qr-image {
  width: 192px;
  height: 192px;
  border: 1px solid $border-color;
  border-radius: 8px;
  background: #fff;
  padding: 8px;
}

.weixin-qr-caption {
  font-size: 13px;
  color: $text-secondary;
}

.weixin-qr-link {
  max-width: 100%;
  color: $accent-primary;
  font-size: 12px;
  line-height: 1.5;
  overflow-wrap: anywhere;
  word-break: break-all;
}

.weixin-qr-hint {
  font-size: 13px;
  color: $text-secondary;
}

.weixin-qr-error {
  margin-top: 8px;
  font-size: 13px;
  color: $error;
}
</style>
