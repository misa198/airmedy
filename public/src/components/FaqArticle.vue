<script setup lang="ts">
import DOMPurify from 'dompurify'
import { marked } from 'marked'
import { computed } from 'vue'

const props = defineProps<{ markdown: string }>()
const html = computed(() => DOMPurify.sanitize(marked.parse(props.markdown) as string))
</script>

<template>
  <article class="faq-markdown" v-html="html" />
</template>
