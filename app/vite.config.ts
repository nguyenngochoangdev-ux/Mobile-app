import { defineConfig } from 'vite'
import react from '@vitejs/plugin-react'
import tailwindcss from '@tailwindcss/vite'
import { VitePWA } from 'vite-plugin-pwa'

export default defineConfig({
  plugins: [
    react(),
    tailwindcss(),
    VitePWA({
      registerType: 'autoUpdate',
      manifest: {
        name: 'Sổ tay hoạt động sinh viên',
        short_name: 'Sổ tay SV',
        description: 'Điểm danh và theo dõi điểm rèn luyện',
        lang: 'vi',
        theme_color: '#0f172a',
        background_color: '#0f172a',
        display: 'standalone',
        start_url: '/',
        icons: [
          { src: '/icon-192.png', sizes: '192x192', type: 'image/png' },
          { src: '/icon-512.png', sizes: '512x512', type: 'image/png' },
        ],
      },
      workbox: {
        // KHÔNG cache /api. Check-in phải đi thẳng tới server, hoặc vào hàng đợi
        // offline của chính app (idb-keyval). Service worker cache ở giữa sẽ làm
        // sai lệch thời điểm quét — thứ quyết định tính hợp lệ của token QR.
        navigateFallbackDenylist: [/^\/api/],
        runtimeCaching: [],
      },
      devOptions: { enabled: false },
    }),
  ],
  server: {
    port: 5173,
    proxy: {
      '/api': { target: 'http://localhost:8080', changeOrigin: true },
      '/v3': { target: 'http://localhost:8080', changeOrigin: true },
    },
  },
})
