import { defineConfig } from 'vite';
import react from '@vitejs/plugin-react';

// The Spring Boot backend serves this build at http://localhost:8080/play
export default defineConfig({
  plugins: [react()],
  base: '/play/',
  build: {
    outDir: '../backend/src/main/resources/static/play',
    emptyOutDir: true,
  },
});
