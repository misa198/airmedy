<script setup lang="ts">
import { computed } from 'vue'
import { RouterLink, useRoute } from 'vue-router'
import FaqArticle from '../components/FaqArticle.vue'
import { faqs } from '../lib/faq'

const route = useRoute()
const selected = computed(() => faqs.find(faq => faq.slug === route.params.slug))
</script>

<template>
  <main><section class="faq-page container"><div class="faq-layout">
    <aside class="faq-sidebar-shell"><nav class="faq-sidebar">
      <RouterLink v-for="faq in faqs" :key="faq.slug" :to="`/faq/${faq.slug}/`" :class="{ active: selected?.slug === faq.slug }">{{ faq.title }}</RouterLink>
    </nav></aside>
    <div class="faq-content">
      <FaqArticle v-if="selected" :markdown="selected.body" :images="selected.images" />
      <article v-else-if="!route.params.slug"><h1>Frequently Asked Questions</h1><p>Select a question to view its answer.</p></article>
      <article v-else><h1>FAQ not found</h1><p>The requested question does not exist.</p></article>
    </div>
  </div></section></main>
</template>
