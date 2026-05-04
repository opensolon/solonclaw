<script setup lang="ts">
import { onMounted, ref } from "vue";
import { NButton, NInput, NPopconfirm, useMessage } from "naive-ui";
import { useI18n } from "vue-i18n";
import { clearApiKey, setApiKey } from "@/api/client";
import { fetchRuntimeConfigItems, revealRuntimeConfigItem, setRuntimeConfigItem } from "@/api/hermes/config";

const ACCESS_TOKEN_KEY = "solonclaw.dashboard.accessToken";

const { t } = useI18n();
const message = useMessage();

const loading = ref(false);
const saving = ref(false);
const revealing = ref(false);
const configured = ref(false);
const tokenPreview = ref("");
const accessToken = ref("");

onMounted(loadTokenStatus);

async function loadTokenStatus() {
  loading.value = true;
  try {
    const items = await fetchRuntimeConfigItems();
    const item = items[ACCESS_TOKEN_KEY];
    configured.value = !!item?.is_set;
    tokenPreview.value = item?.redacted_value || "";
  } catch (err: any) {
    message.error(err.message || t("common.fetchFailed"));
  } finally {
    loading.value = false;
  }
}

async function saveAccessToken() {
  const nextToken = accessToken.value.trim();
  if (!nextToken) {
    message.error(t("account.accessTokenRequired"));
    return;
  }

  saving.value = true;
  try {
    await setRuntimeConfigItem(ACCESS_TOKEN_KEY, nextToken);
    setApiKey(nextToken);
    accessToken.value = "";
    await loadTokenStatus();
    message.success(t("account.accessTokenSaved"));
    window.location.reload();
  } catch (err: any) {
    message.error(err.message || t("common.saveFailed"));
  } finally {
    saving.value = false;
  }
}

async function revealAccessToken() {
  revealing.value = true;
  try {
    accessToken.value = await revealRuntimeConfigItem(ACCESS_TOKEN_KEY);
  } catch (err: any) {
    message.error(err.message || t("account.accessTokenRevealFailed"));
  } finally {
    revealing.value = false;
  }
}

async function clearAccessToken() {
  saving.value = true;
  try {
    await setRuntimeConfigItem(ACCESS_TOKEN_KEY, "");
    accessToken.value = "";
    tokenPreview.value = "";
    configured.value = false;
    clearApiKey();
    await loadTokenStatus();
    message.success(t("account.accessTokenCleared"));
    window.location.reload();
  } catch (err: any) {
    message.error(err.message || t("common.saveFailed"));
  } finally {
    saving.value = false;
  }
}
</script>

<template>
  <div class="account-settings">
    <p class="section-desc">{{ t("account.accessTokenDescription") }}</p>

    <div class="token-card">
      <div class="token-header">
        <div>
          <div class="token-title">{{ t("account.dashboardAccessToken") }}</div>
          <code class="config-key">{{ ACCESS_TOKEN_KEY }}</code>
        </div>
        <span class="status-value" :class="{ configured }">
          {{ configured ? t("common.configured") : t("common.notConfigured") }}
        </span>
      </div>

      <div v-if="tokenPreview" class="token-preview-row">
        <span>{{ t("account.currentValue") }}</span>
        <code class="token-preview">{{ tokenPreview }}</code>
      </div>

      <NInput
        v-model:value="accessToken"
        type="password"
        show-password-on="click"
        :placeholder="t('account.accessTokenPlaceholder')"
        :disabled="loading || saving"
        @keyup.enter="saveAccessToken"
      />

      <div class="action-buttons">
        <NButton type="primary" :loading="saving" :disabled="loading" @click="saveAccessToken">
          {{ t("common.save") }}
        </NButton>
        <NButton :loading="revealing" :disabled="!configured || loading || saving" @click="revealAccessToken">
          {{ t("account.revealAccessToken") }}
        </NButton>
        <NPopconfirm
          :positive-text="t('common.confirm')"
          :negative-text="t('common.cancel')"
          :disabled="!configured"
          @positive-click="clearAccessToken"
        >
          <template #trigger>
            <NButton type="error" quaternary :disabled="!configured || loading || saving">
              {{ t("account.clearAccessToken") }}
            </NButton>
          </template>
          {{ t("account.clearAccessTokenConfirm") }}
        </NPopconfirm>
      </div>
    </div>
  </div>
</template>

<style scoped lang="scss">
@use "@/styles/variables" as *;

.account-settings {
  margin-top: 16px;
}

.section-desc {
  margin: 0 0 16px;
  color: $text-secondary;
  font-size: 13px;
  line-height: 1.6;
}

.token-card {
  border: 1px solid $border-color;
  border-radius: $radius-md;
  padding: 16px;
  background: $bg-card;
  max-width: 720px;
}

.token-header {
  display: flex;
  justify-content: space-between;
  align-items: flex-start;
  gap: 12px;
  margin-bottom: 14px;
}

.token-title {
  font-size: 14px;
  font-weight: 600;
  color: $text-primary;
  margin-bottom: 4px;
}

.config-key,
.token-preview {
  font-family: ui-monospace, SFMono-Regular, Menlo, Monaco, Consolas, "Liberation Mono", "Courier New", monospace;
  font-size: 12px;
}

.config-key {
  color: $text-secondary;
}

.status-value {
  color: $text-muted;
  font-size: 12px;
  white-space: nowrap;

  &.configured {
    color: $success;
  }
}

.token-preview-row {
  display: flex;
  align-items: center;
  gap: 8px;
  margin-bottom: 12px;
  color: $text-muted;
  font-size: 12px;
}

.token-preview {
  color: $text-secondary;
}

.action-buttons {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
  margin-top: 12px;
}
</style>

