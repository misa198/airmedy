<script setup lang="ts">
import { Copy } from '@lucide/vue'
import { Radio } from '@airmedy/ui'
import { useI18n } from 'vue-i18n'
import { useNetworkInterface, type NetworkInterfaceAddress } from '@/composables/useNetworkInterface'

export interface NetworkAddressEntry extends NetworkInterfaceAddress {
  value: string
  display: string
}

const props = defineProps<{
  entries: NetworkAddressEntry[]
  modelValue: string
  copiedValue?: string
  showCopy?: boolean
}>()

const emit = defineEmits<{
  'update:modelValue': [value: string]
  copy: [entry: NetworkAddressEntry]
}>()

const { t } = useI18n()
const { getInterfaceIcon, getInterfaceLabel, getInterfaceTooltip } = useNetworkInterface()
</script>

<template>
  <div class="p-5">
    <p class="mb-3 text-sm font-semibold">{{ t('settings.remote.access_urls') }}</p>
    <div class="space-y-2">
      <div
        v-for="entry in props.entries"
        :key="entry.value"
        class="flex cursor-pointer flex-col gap-2 rounded-xl border bg-foreground/[0.02] p-3 transition-all duration-200"
        :class="props.modelValue === entry.value ? 'border-foreground/20 bg-foreground/[0.03]' : 'border-foreground/[0.04]'"
        @click="emit('update:modelValue', entry.value)"
      >
        <div class="flex select-none items-center gap-1.5 text-xs font-medium text-dim" :title="getInterfaceTooltip(entry.kind, entry.iface)">
          <component :is="getInterfaceIcon(entry.kind, entry.iface)" class="size-3.5 shrink-0" />
          <span>{{ getInterfaceLabel(entry.kind, entry.iface) }}</span>
        </div>
        <div class="flex min-w-0 items-center justify-between gap-2">
          <div class="flex min-w-0 items-center gap-2">
            <Radio :value="entry.value" :model-value="props.modelValue" @update:model-value="emit('update:modelValue', String($event))" />
            <code class="truncate font-mono text-xs text-foreground opacity-80 select-all">{{ entry.display }}</code>
          </div>
          <button v-if="props.showCopy !== false" class="shrink-0 rounded p-1 text-xs text-subdued transition-opacity hover:bg-foreground/[0.04]" @click.prevent.stop="emit('copy', entry)">
            <span v-if="props.copiedValue === entry.value" class="text-xs font-semibold">{{ t('settings.remote.copied') }}</span>
            <Copy v-else class="size-4" />
          </button>
        </div>
      </div>
    </div>
  </div>
</template>
