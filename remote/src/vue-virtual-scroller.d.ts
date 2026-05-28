declare module 'vue-virtual-scroller' {
  import { Component, Plugin } from 'vue'
  const plugin: Plugin
  export const RecycleScroller: Component
  export const DynamicScroller: Component
  export const DynamicScrollerItem: Component
  export default plugin
}
