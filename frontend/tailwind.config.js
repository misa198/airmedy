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
        foreground: '#FFFFFF',
        glass: 'var(--bg-glass)',
        'glass-elevated': 'var(--bg-glass-elevated)',
        'border-glass': 'var(--border-glass)',
        border: 'rgba(255, 255, 255, 0.1)',
        primary: {
          DEFAULT: 'var(--primary)',
          foreground: '#FFFFFF',
        },
        card: {
          DEFAULT: '#1A1A1A',
          foreground: '#FFFFFF',
        },
        accent: {
          DEFAULT: '#2D2D2D',
          foreground: '#FFFFFF',
        },
        muted: {
          DEFAULT: '#27272A',
          foreground: 'var(--text-muted)',
        },
        sidebar: {
          DEFAULT: '#0A0A0A',
          foreground: '#FFFFFF',
          accent: '#2D2D2D',
          'accent-foreground': '#FFFFFF',
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