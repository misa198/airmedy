<script setup lang="ts">
</script>

<template>
  <div class="p-6">
    <h1 class="text-3xl font-bold mb-6">{{ $t('home.title', 'Home') }}</h1>
    
    <div class="grid grid-cols-1 md:grid-cols-2 lg:grid-cols-3 gap-6">
      <div class="bg-card p-4 rounded-lg shadow h-64 flex items-center justify-center border">
        <p class="text-muted-foreground">{{ $t('home.recently_listened', 'Recently Listened Placeholder') }}</p>
      </div>
      <div class="bg-card p-4 rounded-lg shadow h-64 flex items-center justify-center border">
        <p class="text-muted-foreground">{{ $t('home.most_listened', 'Most Listened Placeholder') }}</p>
      </div>
      <div class="bg-card p-4 rounded-lg shadow h-64 flex items-center justify-center border">
        <p class="text-muted-foreground">{{ $t('home.suggested', 'Suggested Placeholder') }}</p>
      </div>
    </div>
  </div>
</template>
