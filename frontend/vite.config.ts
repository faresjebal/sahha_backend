import { defineConfig } from 'vite'
import react from '@vitejs/plugin-react'

export default defineConfig({
  plugins: [react()],
  build: {
    rolldownOptions: {
      output: {
        manualChunks(id) {
          if (id.includes('node_modules/gsap') || id.includes('node_modules/@gsap')) return 'motion'
          if (id.includes('node_modules/lucide-react')) return 'icons'
          if (id.includes('node_modules/react')) return 'react-vendor'
        },
      },
    },
  },
})
