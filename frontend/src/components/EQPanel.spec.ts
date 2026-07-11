import { beforeEach, describe, expect, it, vi } from 'vitest'
import { mount } from '@vue/test-utils'
import { createTestingPinia } from '@pinia/testing'
import EQPanel from './EQPanel.vue'
import { useAppStore } from '@/stores/app'
import * as EQService from '../../bindings/airmedy/internal/infra/wails/eqservice'

vi.mock('../../bindings/airmedy/internal/infra/wails/eqservice', () => ({
  ApplyProfile: vi.fn(),
  CreateProfile: vi.fn(),
  DeleteProfile: vi.fn(),
  GetActiveProfile: vi.fn(),
  GetAllProfiles: vi.fn(),
  RenameProfile: vi.fn(),
  UpdateBand: vi.fn(),
}))

vi.mock('@wailsio/runtime', async (importOriginal) => ({
  ...await importOriginal<typeof import('@wailsio/runtime')>(),
  Events: { On: vi.fn(() => () => {}) },
}))

vi.mock('vue-i18n', () => ({
  useI18n: () => ({ t: (key: string) => key }),
}))

const profiles = [
  {
    id: 'flat', name: 'Flat', is_active: true, is_default: true,
    bands: Array.from({ length: 10 }, (_, index) => ({ index, frequency: 32, gain: 0, bandwidth: 1 })),
  },
  {
    id: 'rock', name: 'Rock', is_active: false, is_default: true,
    bands: Array.from({ length: 10 }, (_, index) => ({ index, frequency: 32, gain: 1, bandwidth: 1 })),
  },
]

function mountPanel() {
  return mount(EQPanel, {
    global: {
      plugins: [createTestingPinia({ createSpy: vi.fn, initialState: { app: { eqPreamp: -2 } } })],
      stubs: {
        Slider: { template: '<button class="slider" @click="$emit(\'update:modelValue\', 3)" @mouseup="$emit(\'mouseup\')" />' },
        Select: { template: '<div><slot /></div>' },
        SelectTrigger: { template: '<div><slot /></div>' },
        SelectValue: true,
        SelectContent: { template: '<div><slot /></div>' },
        SelectItem: { template: '<div><slot /></div>' },
        ContextMenu: true,
        EQProfileDialog: true,
      },
    },
  })
}

describe('EQPanel', () => {
  beforeEach(() => {
    vi.clearAllMocks()
    vi.mocked(EQService.GetAllProfiles).mockResolvedValue(profiles as never)
    vi.mocked(EQService.GetActiveProfile).mockResolvedValue(profiles[0] as never)
  })

  it('keeps the global preamp unchanged when a preset is selected', async () => {
    const wrapper = mountPanel()
    await vi.waitFor(() => expect(EQService.GetAllProfiles).toHaveBeenCalled())
    const store = useAppStore()

    await (wrapper.vm as unknown as { selectProfile: (id: string) => Promise<void> }).selectProfile('rock')

    expect(EQService.ApplyProfile).toHaveBeenCalledWith('rock')
    expect(store.eqPreamp).toBe(-2)
    expect(store.updateEQPreamp).not.toHaveBeenCalled()
  })
})
