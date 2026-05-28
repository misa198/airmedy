import { defineConfig, type Plugin } from "vite";
import vue from "@vitejs/plugin-vue";
import wails from "@wailsio/runtime/plugins/vite";
import path from "path";
import { existsSync } from "node:fs";

// Stubs missing Wails bindings during the brief deletion window when
// `wails3 generate bindings -clean=true` wipes and regenerates the dir.
// Uses the real file path (not a virtual ID) so Vite's watcher invalidates
// the stub automatically when the real file is recreated.
function wailsBindingsStub(): Plugin {
  return {
    name: "wails-bindings-stub",
    enforce: "pre",
    apply: "serve",
    resolveId(id, importer) {
      if (!importer || !id.startsWith(".")) return;
      const abs = path.resolve(path.dirname(importer), id);
      if (!abs.includes("/bindings/")) return;
      const absBase = abs.replace(/\.js$/, "");
      if (existsSync(abs) || existsSync(absBase + ".ts")) return;
      return absBase + ".ts";
    },
    load(id) {
      if (!id.includes("/bindings/") || existsSync(id)) return;
      return "export {}";
    },
  };
}

// https://vitejs.dev/config/
export default defineConfig(({ mode }) => ({
  plugins: [wailsBindingsStub(), vue(), wails("./bindings")],
  resolve: {
    alias: {
      "@": path.resolve(__dirname, "./src"),
      "@airmedy/ui": path.resolve(__dirname, "../packages/ui/src"),
      "@airmedy/utils": path.resolve(__dirname, "../packages/utils/src"),
    },
  },
  esbuild: mode === 'production' ? { drop: ['console', 'debugger'] } : {},
}));
