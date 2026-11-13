/** @type {import('tailwindcss').Config} */
export default {
  content: [
    "./index.html",
    "./src/**/*.{js,ts,jsx,tsx}",
  ],
  theme: {
    extend: {
      colors: {
        bgDark: '#0B0F19',
        surfaceDark: '#111827',
        cardDark: '#1F2937',
        borderDark: '#374151',
        accentBlue: '#3B82F6',
        accentCyan: '#06B6D4',
        accentEmerald: '#10B981',
        accentAmber: '#F59E0B',
      }
    },
  },
  plugins: [],
}

