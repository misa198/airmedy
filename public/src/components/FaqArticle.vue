<script setup lang="ts">
import DOMPurify from 'dompurify'
import { marked } from 'marked'
import { computed } from 'vue'

const props = defineProps<{
  markdown: string
  /** Map from bare filename to Vite-resolved URL (from faq.ts) */
  images?: Record<string, string>
}>()

const html = computed(() => {
  const raw = marked.parse(props.markdown) as string
  const images = props.images ?? {}

  // Replace relative image src/href values with Vite-resolved URLs so that
  // images bundled alongside folder-based FAQ entries load correctly.
  const rewritten = raw.replace(
    /(<img\s[^>]*src=["'])(\.[^"']+)(["'])/gi,
    (_match, pre, src, post) => {
      const base = src.replace(/^\.\//, '')
      return pre + (images[base] ?? src) + post
    },
  ).replace(
    /(<a\s[^>]*href=["'])(\.[^"']+)(["'])/gi,
    (_match, pre, href, post) => {
      const base = href.replace(/^\.\//, '')
      return pre + (images[base] ?? href) + post
    },
  )

  return DOMPurify.sanitize(rewritten)
})
</script>

<template>
  <article class="faq-markdown" v-html="html" />
</template>
