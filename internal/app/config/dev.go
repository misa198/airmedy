//go:build !production

package config

const appDataFolder = "airmedy-dev"

// IsProduction is false in development builds.
const IsProduction = false
