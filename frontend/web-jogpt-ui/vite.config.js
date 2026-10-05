import { defineConfig } from 'vite'
import react from '@vitejs/plugin-react'

// https://vite.dev/config/
export default defineConfig({
  plugins: [react()],
  server: {
    port: 5173,       // 포트 5173 고정
    strictPort: true, // 5173이 이미 사용 중이면 에러 (다른 포트로 넘어가지 않음)
  },
})
