<script setup lang="ts">
</script>

<template>
  <div class="p-6">
    <h1 class="text-3xl font-bold mb-6">{{ $t('home.title') }}</h1>
    
    <div class="grid grid-cols-1 md:grid-cols-2 lg:grid-cols-3 gap-6">
      <div class="bg-card p-4 rounded-lg h-64 flex items-center justify-center ring-1 ring-foreground/[0.06]">
        <p class="text-foreground/40">{{ $t('home.recently_listened') }}</p>
      </div>
      <div class="bg-card p-4 rounded-lg h-64 flex items-center justify-center ring-1 ring-foreground/[0.06]">
        <p class="text-foreground/40">{{ $t('home.most_listened') }}</p>
      </div>
      <div class="bg-card p-4 rounded-lg h-64 flex items-center justify-center ring-1 ring-foreground/[0.06]">
        <p class="text-foreground/40">{{ $t('home.suggested') }}</p>
      </div>
    </div>
  </div>
</template>
