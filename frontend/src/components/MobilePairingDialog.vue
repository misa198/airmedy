<script setup lang="ts">
import { Modal } from '@airmedy/ui'
import { Smartphone, ShieldCheck } from '@lucide/vue'
import { useI18n } from 'vue-i18n'
import { useMobilePairingStore } from '@/stores/mobilePairing'

const { t } = useI18n()
const pairingStore = useMobilePairingStore()
</script>

<template>
  <Modal
    :open="!!pairingStore.pendingRequest"
    :title="t('settings.mobile_pairing.request_title')"
    width-class="w-[30rem]"
    @close="pairingStore.respond(false)"
  >
    <div v-if="pairingStore.pendingRequest" class="space-y-4">
      <div class="flex items-center gap-3 rounded-xl border border-foreground/[0.1] bg-foreground/[0.04] p-4">
        <div class="flex size-10 items-center justify-center rounded-full bg-primary text-primary-foreground">
          <Smartphone class="size-5" />
        </div>
        <div class="min-w-0">
          <p class="font-medium text-foreground opacity-90 truncate">{{ pairingStore.pendingRequest.display_name }}</p>
          <p class="text-xs text-dimmer">{{ pairingStore.pendingRequest.platform }}</p>
        </div>
      </div>
      <p class="text-sm leading-relaxed text-dim">{{ t('settings.mobile_pairing.request_desc') }}</p>
      <div class="rounded-lg border border-foreground/[0.1] bg-foreground/[0.03] p-3">
        <div class="mb-1 flex items-center gap-1.5 text-xs text-dimmer"><ShieldCheck class="size-3.5" /> {{ t('settings.mobile_pairing.fingerprint') }}</div>
        <code class="text-xs text-foreground opacity-75 select-all">{{ pairingStore.pendingRequest.fingerprint }}</code>
      </div>
    </div>
    <template #footer>
      <div class="flex justify-end gap-2">
        <button :disabled="pairingStore.responding" class="rounded-lg px-3 py-1.5 text-sm text-subdued transition-all hover:bg-foreground/[0.04] hover:opacity-70 disabled:opacity-30" @click="pairingStore.respond(false)">
          {{ t('settings.mobile_pairing.decline') }}
        </button>
        <button :disabled="pairingStore.responding" class="rounded-lg bg-primary px-3 py-1.5 text-sm font-medium text-primary-foreground transition-all hover:scale-[1.02] disabled:opacity-50" @click="pairingStore.respond(true)">
          {{ t('settings.mobile_pairing.accept') }}
        </button>
      </div>
    </template>
  </Modal>
</template>
