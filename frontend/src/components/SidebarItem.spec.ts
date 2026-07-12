import { describe, expect, it } from 'vitest'
import { mount } from '@vue/test-utils'
import { markRaw } from 'vue'
import SidebarItem from './SidebarItem.vue'

describe('SidebarItem', () => {
  it('truncates a long label while preserving the navigation icon size', () => {
    const wrapper = mount(SidebarItem, {
      props: {
        to: '/playlists/long-name',
        icon: markRaw({ template: '<svg />' }),
        label: 'A very long playlist name that should be truncated',
      },
      global: {
        stubs: { RouterLink: { template: '<a><slot /></a>' } },
      },
    })

    expect(wrapper.find('svg').classes()).toContain('flex-shrink-0')
    expect(wrapper.find('span').classes()).toEqual(expect.arrayContaining(['min-w-0', 'flex-1', 'truncate']))
  })
})
