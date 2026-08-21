import { Cable, EthernetPort, GlobeLock, Layers2, Waypoints, Wifi } from '@lucide/vue'
import { useI18n } from 'vue-i18n'

export interface NetworkInterfaceAddress {
  ip: string
  iface: string
  kind: string
}

export function useNetworkInterface() {
  const { t } = useI18n()

  function isVirtualMachineInterface(iface: string) {
    const name = iface.toLowerCase()
    return ['vbox', 'vmnet', 'vnic', 'hyper-v', 'vnet', 'virtual'].some(part => name.includes(part))
  }

  function getInterfaceIcon(kind: string, iface: string) {
    switch (kind.toLowerCase()) {
      case 'wifi': return Wifi
      case 'ethernet': return EthernetPort
      case 'vpn': return GlobeLock
      case 'link_local':
      case 'link-local': return Cable
      case 'virtual': return isVirtualMachineInterface(iface) ? Layers2 : Waypoints
      default: return Wifi
    }
  }

  function getInterfaceLabel(kind: string, iface: string) {
    switch (kind.toLowerCase()) {
      case 'wifi': return t('settings.remote.interface_wifi')
      case 'ethernet': return t('settings.remote.interface_ethernet')
      case 'vpn': return t('settings.remote.interface_vpn')
      case 'link_local':
      case 'link-local': return t('settings.remote.interface_link_local')
      case 'virtual': return t(isVirtualMachineInterface(iface) ? 'settings.remote.interface_virtual_vm' : 'settings.remote.interface_virtual')
      default: return kind
    }
  }

  function getInterfaceTooltip(kind: string, iface: string) {
    return `${getInterfaceLabel(kind, iface)} (${iface})`
  }

  return { getInterfaceIcon, getInterfaceLabel, getInterfaceTooltip }
}
