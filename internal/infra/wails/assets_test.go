package wails

import (
	"bytes"
	"net/http"
	"net/http/httptest"
	"testing"
)

type countingResponseWriter struct {
	http.ResponseWriter
	writes int
}

func (w *countingResponseWriter) Write(data []byte) (int, error) {
	w.writes++
	return w.ResponseWriter.Write(data)
}

func TestServeArtworkVariantSingleWrite(t *testing.T) {
	data := bytes.Repeat([]byte("jpeg"), 20_000)
	response := httptest.NewRecorder()
	w := &countingResponseWriter{ResponseWriter: response}
	serveArtworkVariant(w, httptest.NewRequest(http.MethodGet, "/artwork/test.jpg?size=md", nil), data)

	if w.writes != 1 || !bytes.Equal(response.Body.Bytes(), data) {
		t.Fatalf("variant response: %d writes, %d of %d bytes", w.writes, response.Body.Len(), len(data))
	}
	if response.Header().Get("Content-Type") != "image/jpeg" || response.Header().Get("Content-Length") != "80000" {
		t.Fatalf("variant headers: %v", response.Header())
	}
}
