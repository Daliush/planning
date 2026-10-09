import { defineConfig } from "vite";
import react from "@vitejs/plugin-react";

// Le proxy de développement conserve la même API relative que le frontend Docker.
export default defineConfig({
  plugins: [react()],
  server: {
    port: 5174,
    proxy: {
      "/api": {
        target: process.env.PLANNING_API_URL ?? "http://localhost:8081",
        changeOrigin: true,
      },
    },
  },
});
