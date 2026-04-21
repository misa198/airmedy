/** @type {import('tailwindcss').Config} */
export default {
  content: [
    "./index.html",
    "./src/**/*.{vue,js,ts,jsx,tsx}",
  ],
  theme: {
    extend: {
      colors: {
        background: 'var(--bg-main)',
        glass: 'var(--bg-glass)',
        'glass-elevated': 'var(--bg-glass-elevated)',
        'border-glass': 'var(--border-glass)',
        primary: {
          DEFAULT: 'var(--primary)',
          foreground: '#FFFFFF',
        },
        muted: {
          DEFAULT: '#27272A',
          foreground: 'var(--text-muted)',
        },
        dynamic: {
          primary: 'var(--dynamic-primary)',
          surface: 'var(--dynamic-surface)',
          glow: 'var(--dynamic-glow)',
        }
      },
      backgroundImage: {
        'primary-gradient': 'var(--primary-gradient)',
      },
      borderRadius: {
        lg: '12px',
        md: '8px',
        sm: '4px',
      }
    },
  },
  plugins: [],
}