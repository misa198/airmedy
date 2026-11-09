//go:build !production

package wails

import (
	"net"
	"net/http"
	"net/http/httptest"
	"sync/atomic"
	"testing"
	"testing/fstest"
)

func TestDevAssetHandlerRetriesResetFrontendConnection(t *testing.T) {
	var requests atomic.Int32
	frontend := httptest.NewServer(http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
		if requests.Add(1) == 1 {
			conn, _, err := w.(http.Hijacker).Hijack()
			if err != nil {
				t.Errorf("hijack connection: %v", err)
				return
			}
			if tcp, ok := conn.(*net.TCPConn); ok {
				_ = tcp.SetLinger(0)
			}
			_ = conn.Close()
			return
		}
		_, _ = w.Write([]byte("vite ready"))
	}))
	defer frontend.Close()
	t.Setenv("FRONTEND_DEVSERVER_URL", frontend.URL)

	assets := fstest.MapFS{"frontend/dist/index.html": &fstest.MapFile{Data: []byte("embedded")}}
	handler := NewAssetHandler(assets, nil)
	response := httptest.NewRecorder()
	handler.ServeHTTP(response, httptest.NewRequest(http.MethodGet, "/", nil))

	if response.Code != http.StatusOK || response.Body.String() != "vite ready" || requests.Load() != 2 {
		t.Fatalf("dev asset response: status=%d body=%q requests=%d", response.Code, response.Body.String(), requests.Load())
	}
}
