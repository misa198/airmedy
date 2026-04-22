package wails

import (
	"embed"
	"io/fs"
	"net/http"
	"os"
	"strings"

	"changeme/internal/domain"
)

func NewAssetHandler(assets embed.FS, artworkCache domain.ArtworkCache) http.Handler {
	distFS, err := fs.Sub(assets, "frontend/dist")
	if err != nil {
		panic(err)
	}

	embeddedHandler := http.FileServer(http.FS(distFS))

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

			http.ServeFile(w, r, filePath)
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
