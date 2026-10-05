import { defineConfig } from 'vite';
import react from '@vitejs/plugin-react';

export default defineConfig({
    plugins: [react()],
    server: {
        port: 5173,
        strictPort: true,
    },
    build: {
        // Electron에서 파일 경로로 열 때 상대경로 필요
        base: './',
        outDir: 'dist',
    },
});
