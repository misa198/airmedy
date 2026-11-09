import { describe, expect, it } from 'vitest'
import { compileStyle, parse } from 'vue/compiler-sfc'
import source from './GroupedAlbumList.vue?raw'
import trackCardSource from './TrackCard.vue?raw'

describe('GroupedAlbumList artwork', () => {
  it('uses the same scoped artwork hover animation as TrackCard', () => {
    const compile = (component: string) => {
      const { descriptor } = parse(component)
      return compileStyle({
        source: descriptor.styles[0]!.content,
        filename: 'Artwork.vue',
        id: 'data-v-test',
        scoped: true,
      }).code
    }

    const artworkStyles = compile(trackCardSource).replaceAll('track-artwork', 'album-artwork')
    expect(compile(source)).toContain(artworkStyles.trim())
  })
})
