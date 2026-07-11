package artwork

import (
	"fmt"
	"image"
	"image/color"
	"image/draw"
	_ "image/jpeg"
	_ "image/png"
	"math"
	"os"

	"airmedy/internal/domain"
)

// ExtractPalette reads an image file and returns a dominant color palette.
// It downsamples to a 64×64 thumbnail, runs 3-cluster k-means for 10 iterations,
// and classifies clusters as Vibrant (highest saturation×value), Dominant (largest),
// and Muted (remaining).
func ExtractPalette(imagePath string) (*domain.ThemeColors, error) {
	f, err := os.Open(imagePath)
	if err != nil {
		return nil, fmt.Errorf("open image: %w", err)
	}
	defer func() { _ = f.Close() }()

	src, _, err := image.Decode(f)
	if err != nil {
		return nil, fmt.Errorf("decode image: %w", err)
	}

	thumb := downsample(src, 64, 64)
	pixels := collectPixels(thumb)
	if len(pixels) == 0 {
		return &domain.ThemeColors{
			Vibrant:  "#E11D48",
			Muted:    "#6B7280",
			Dominant: "#1F2937",
		}, nil
	}

	centers := kMeans(pixels, 3, 10)

	// Count pixels per cluster for dominance classification
	counts := make([]int, len(centers))
	for _, p := range pixels {
		closest := nearestCenter(p, centers)
		counts[closest]++
	}

	vibrantIdx := mostVibrant(centers)
	dominantIdx := largest(counts)
	mutedIdx := remaining(vibrantIdx, dominantIdx, len(centers))

	return &domain.ThemeColors{
		Vibrant:  toHex(centers[vibrantIdx]),
		Muted:    toHex(centers[mutedIdx]),
		Dominant: toHex(centers[dominantIdx]),
	}, nil
}

func downsample(src image.Image, maxW, maxH int) *image.RGBA {
	bounds := src.Bounds()
	srcW, srcH := bounds.Dx(), bounds.Dy()

	w, h := srcW, srcH
	if w > maxW {
		h = h * maxW / w
		w = maxW
	}
	if h > maxH {
		w = w * maxH / h
		h = maxH
	}
	if w < 1 {
		w = 1
	}
	if h < 1 {
		h = 1
	}

	dst := image.NewRGBA(image.Rect(0, 0, w, h))
	scaleX := float64(srcW) / float64(w)
	scaleY := float64(srcH) / float64(h)

	for y := 0; y < h; y++ {
		for x := 0; x < w; x++ {
			sx := int(float64(x) * scaleX)
			sy := int(float64(y) * scaleY)
			r, g, b, a := src.At(bounds.Min.X+sx, bounds.Min.Y+sy).RGBA()
			dst.SetRGBA(x, y, color.RGBA{
				R: uint8(r >> 8),
				G: uint8(g >> 8),
				B: uint8(b >> 8),
				A: uint8(a >> 8),
			})
		}
	}
	return dst
}

func collectPixels(img draw.Image) []color.RGBA {
	bounds := img.Bounds()
	pixels := make([]color.RGBA, 0, bounds.Dx()*bounds.Dy())
	for y := bounds.Min.Y; y < bounds.Max.Y; y++ {
		for x := bounds.Min.X; x < bounds.Max.X; x++ {
			r, g, b, a := img.At(x, y).RGBA()
			if a < 0x8000 {
				continue // skip mostly transparent pixels
			}
			pixels = append(pixels, color.RGBA{
				R: uint8(r >> 8),
				G: uint8(g >> 8),
				B: uint8(b >> 8),
				A: 0xFF,
			})
		}
	}
	return pixels
}

func kMeans(pixels []color.RGBA, k, iterations int) []color.RGBA {
	if len(pixels) < k {
		k = len(pixels)
	}
	// Seed centers evenly across pixels
	centers := make([]color.RGBA, k)
	step := len(pixels) / k
	for i := range centers {
		centers[i] = pixels[i*step]
	}

	assignments := make([]int, len(pixels))
	for iter := 0; iter < iterations; iter++ {
		changed := false
		for i, p := range pixels {
			c := nearestCenter(p, centers)
			if assignments[i] != c {
				assignments[i] = c
				changed = true
			}
		}
		if !changed {
			break
		}
		// Recompute centers
		sums := make([][3]int64, k)
		counts := make([]int, k)
		for i, p := range pixels {
			c := assignments[i]
			sums[c][0] += int64(p.R)
			sums[c][1] += int64(p.G)
			sums[c][2] += int64(p.B)
			counts[c]++
		}
		for i := range centers {
			if counts[i] > 0 {
				centers[i] = color.RGBA{
					R: uint8(sums[i][0] / int64(counts[i])),
					G: uint8(sums[i][1] / int64(counts[i])),
					B: uint8(sums[i][2] / int64(counts[i])),
					A: 0xFF,
				}
			}
		}
	}
	return centers
}

func nearestCenter(p color.RGBA, centers []color.RGBA) int {
	best, bestDist := 0, math.MaxFloat64
	for i, c := range centers {
		dr := float64(p.R) - float64(c.R)
		dg := float64(p.G) - float64(c.G)
		db := float64(p.B) - float64(c.B)
		d := dr*dr + dg*dg + db*db
		if d < bestDist {
			best, bestDist = i, d
		}
	}
	return best
}

func mostVibrant(centers []color.RGBA) int {
	best, bestV := 0, -1.0
	for i, c := range centers {
		v := vibrance(c)
		if v > bestV {
			best, bestV = i, v
		}
	}
	return best
}

func largest(counts []int) int {
	best, bestN := 0, -1
	for i, n := range counts {
		if n > bestN {
			best, bestN = i, n
		}
	}
	return best
}

func remaining(a, b, total int) int {
	for i := 0; i < total; i++ {
		if i != a && i != b {
			return i
		}
	}
	return 0
}

// vibrance returns saturation × value (HSV) as a proxy for "how colorful" a pixel is.
func vibrance(c color.RGBA) float64 {
	r, g, b := float64(c.R)/255.0, float64(c.G)/255.0, float64(c.B)/255.0
	maxC := math.Max(r, math.Max(g, b))
	minC := math.Min(r, math.Min(g, b))
	if maxC == 0 {
		return 0
	}
	saturation := (maxC - minC) / maxC
	return saturation * maxC // saturation × value
}

func toHex(c color.RGBA) string {
	return fmt.Sprintf("#%02X%02X%02X", c.R, c.G, c.B)
}
