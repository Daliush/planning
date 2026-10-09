import { defineConfig } from "vite";
import react from "@vitejs/plugin-react";

// Le proxy de développement conserve la même API relative que le frontend Docker.
export default defineConfig({
  plugins: [react()],
  server: {
    port: 5174,
    watch: {
      usePolling: process.env.PLANNING_WATCH_POLLING === "true",
      interval: 250,
    },
    proxy: {
      "/api": {
        target: process.env.PLANNING_API_URL ?? "http://localhost:8081",
        // Conserver Host avec son port pour les origines des instances isolées.
        changeOrigin: false,
      },
    },
  },
});
