package wails

import (
	"embed"
	"io/fs"
	"net/http"
	"net/http/httputil"
	"net/url"
	"os"
	"strings"

	"airmedy/internal/domain"
)

func NewAssetHandler(assets embed.FS, artworkCache domain.ArtworkCache) http.Handler {
	distFS, err := fs.Sub(assets, "frontend/dist")
	if err != nil {
		panic(err)
	}

	embeddedHandler := http.FileServer(http.FS(distFS))

	var devProxy http.Handler
	if !IsProduction {
		target, err := url.Parse("http://localhost:9245")
		if err != nil {
			panic(err)
		}
		devProxy = httputil.NewSingleHostReverseProxy(target)
	}

	return http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
		if strings.HasPrefix(r.URL.Path, "/artwork/") {
			key := strings.TrimPrefix(r.URL.Path, "/artwork/")
			if key == "" {
				http.NotFound(w, r)
				return
			}

			filePath := artworkCache.GetPath(key)
			if _, err := os.Stat(filePath); os.IsNotExist(err) {
				http.NotFound(w, r)
				return
			}

			size := r.URL.Query().Get("size")
			if size == "sm" || size == "md" {
				variantPath := artworkCache.GetVariantPath(key, size)
				if _, err := os.Stat(variantPath); err == nil {
					http.ServeFile(w, r, variantPath)
					return
				}
				// variant missing (pre-existing track) — fall back to original
			}

			http.ServeFile(w, r, filePath)
			return
		}

		if !IsProduction && devProxy != nil {
			devProxy.ServeHTTP(w, r)
			return
		}

		// Check if the file exists in the embedded FS
		// If not, it might be a frontend route, so serve index.html
		filePath := strings.TrimPrefix(r.URL.Path, "/")
		if filePath == "" {
			filePath = "index.html"
		}

		_, err := fs.Stat(distFS, filePath)
		if err != nil && os.IsNotExist(err) {
			// Serve index.html for SPA routing
			r.URL.Path = "/"
		}

		embeddedHandler.ServeHTTP(w, r)
	})
}
