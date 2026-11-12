<script setup lang="ts">
import { type Component, type PropType, toRef } from 'vue';

const props = defineProps({
    label: {
        type: String,
        default: ''
    },
    icon: {
        type: [Object, Function] as PropType<Component>,
        required: true
    },
    filledIcon: {
        type: Boolean,
        default: false,
        required: false
    },
    variant: {
        type: String as PropType<'contained' | 'outline'>,
        default: 'contained',
    }
})

const Icon = toRef(props, 'icon')
</script>

<template>
    <button class="h-10 flex items-center justify-center shadow-lg rounded-full transition-all duration-300 ease-[cubic-bezier(0.4,0,0.2,1)]" :class="{
        'bg-foreground text-background details-button-contained': variant === 'contained',
        'bg-background text-foreground ring-1 ring-foreground/[0.08] hover:bg-foreground/[0.06]': variant === 'outline',
        'w-10': !label,
        'px-4 gap-x-2': label
    }">
        <Icon class="w-5 h-5" :class="filledIcon ? 'fill-current' : ''" />
        <span class="text-sm @lg:text-medium font-bold">{{ label }}</span>
    </button>
</template>
<style scoped>
.details-button-contained {
    transform: translateZ(0) scale(1);
    will-change: transform;
}

.details-button-contained:hover {
    transform: translateZ(0) scale(1.05);
}
</style>
