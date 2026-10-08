import { defineConfig } from "vite";
import react from "@vitejs/plugin-react";

// Le port diffère de celui de Progression : les deux frontends peuvent tourner
// côte à côte pendant la période où le module vit encore dans ce dépôt.
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
