const isDev = import.meta.env.DEV

export const logger = {
  debug: (...args: unknown[]) => isDev && console.debug(...args),
  info: (...args: unknown[]) => isDev && console.info(...args),
  warn: (...args: unknown[]) => console.warn(...args),
  error: (...args: unknown[]) => console.error(...args),
}
