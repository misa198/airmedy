/** @type {import('tailwindcss').Config} */
export default {
  content: [
    "./index.html",
    "./src/**/*.{vue,js,ts,jsx,tsx}",
  ],
  theme: {
    extend: {
      fontFamily: {
        sans: ['Inter', 'ui-sans-serif', 'system-ui', '-apple-system', 'BlinkMacSystemFont', '"Segoe UI"', 'Roboto', '"Helvetica Neue"', 'Arial', '"Noto Sans"', 'sans-serif', '"Apple Color Emoji"', '"Segoe UI Emoji"', '"Segoe UI Symbol"', '"Noto Color Emoji"'],
      },
      colors: {
        background: 'var(--bg-main)',
        foreground: 'var(--text-main)',
        glass: 'var(--bg-glass)',
        'glass-elevated': 'var(--bg-glass-elevated)',
        'border-glass': 'var(--border-glass)',
        border: 'var(--border-glass)',
        primary: {
          DEFAULT: 'var(--primary)',
          foreground: 'var(--primary-foreground)',
        },
        card: {
          DEFAULT: 'var(--bg-glass-elevated)',
          foreground: 'var(--text-main)',
        },
        accent: {
          DEFAULT: 'var(--bg-glass-elevated)',
          foreground: 'var(--text-main)',
        },
        muted: {
          DEFAULT: 'var(--bg-glass)',
          foreground: 'var(--text-muted)',
        },
        sidebar: {
          DEFAULT: 'var(--bg-main)',
          foreground: 'var(--text-main)',
          accent: 'var(--bg-glass-elevated)',
          'accent-foreground': 'var(--text-main)',
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