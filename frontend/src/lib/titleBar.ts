import { Folder, Settings, Smartphone, Wifi } from '@lucide/vue'
import { PlayFill as Play } from '@airmedy/ui'

export type TitleBarActionId = 'mobile' | 'library' | 'settings' | 'playback' | 'remote'
export interface TitleBarAction { id: TitleBarActionId; visible: boolean }

export const TITLE_BAR_ACTIONS = {
  mobile: { path: '/settings/mobile-devices', label: 'settings.categories.mobile_devices', icon: Smartphone },
  library: { path: '/settings/library', label: 'settings.categories.library', icon: Folder },
  settings: { path: '/settings', label: 'settings.title', icon: Settings },
  playback: { path: '/settings/playback', label: 'settings.categories.playback', icon: Play },
  remote: { path: '/settings/remote', label: 'settings.categories.remote', icon: Wifi },
} as const

export function defaultTitleBarActions(): TitleBarAction[] {
  return [
    { id: 'mobile', visible: true }, { id: 'library', visible: true },
    { id: 'settings', visible: true }, { id: 'playback', visible: false },
    { id: 'remote', visible: false },
  ]
}
