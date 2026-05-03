package artwork

import (
	"bytes"
	"fmt"
	"image"
	"image/jpeg"
	_ "image/png"
)

// resizeToJPEG decodes image data, downsamples to fit within maxW×maxH
// (preserving aspect ratio), and re-encodes as JPEG at quality 85.
func resizeToJPEG(data []byte, maxW, maxH int) ([]byte, error) {
	src, _, err := image.Decode(bytes.NewReader(data))
	if err != nil {
		return nil, fmt.Errorf("decode image: %w", err)
	}

	dst := downsample(src, maxW, maxH)

	var buf bytes.Buffer
	if err := jpeg.Encode(&buf, dst, &jpeg.Options{Quality: 85}); err != nil {
		return nil, fmt.Errorf("encode jpeg: %w", err)
	}
	return buf.Bytes(), nil
}
