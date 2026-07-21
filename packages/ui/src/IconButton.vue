<script setup lang="ts">
withDefaults(defineProps<{
  active?: boolean
  size?: 'sm' | 'md'
  variant?: 'ghost' | 'outlined' | 'contained'
}>(), {
  variant: 'ghost',
})
</script>

<template>
  <button
    class="flex shrink-0 items-center justify-center rounded-full aspect-square transition-all duration-200 ease-in-out"
    :class="[
      size === 'sm' ? 'h-8' : 'h-[38px]',
      variant === 'ghost' && !active && 'text-foreground/60 hover:text-foreground/80',
      variant === 'ghost' && active && 'text-primary',
      variant === 'outlined' && !active && 'border border-border bg-accent/50 text-foreground/60 hover:text-foreground/80',
      variant === 'outlined' && active && 'border border-border bg-accent/50 ring-2 ring-primary/40 text-primary',
      variant === 'contained' && !active && 'bg-foreground/[0.06] text-foreground/60 hover:bg-foreground/[0.10]',
      variant === 'contained' && active && 'bg-primary text-primary-foreground',
    ]"
  >
    <slot />
  </button>
</template>
