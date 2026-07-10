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
// then separately scans a 256×256 sample for a recurring saturated accent. The
// accent pass preserves small artwork details (such as coloured title text) that
// are otherwise too small to survive k-means clustering.
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

	vibrant := centers[mostVibrant(centers)]
	if accent, found := mostVibrantAccent(src); found {
		vibrant = accent
	}
	dominantIdx := largest(counts)
	mutedIdx := remaining(mostVibrant(centers), dominantIdx, len(centers))

	return &domain.ThemeColors{
		Vibrant:  toHex(vibrant),
		Muted:    toHex(centers[mutedIdx]),
		Dominant: toHex(centers[dominantIdx]),
	}, nil
}

const accentSampleSize = 256

// accentMinAreaFraction is the minimum fraction of sample pixels a bucket must
// cover to be considered at all. At 256×256 this is ~262 pixels (~0.4%).
// This eliminates single-pixel JPEG artefacts and tiny logos before scoring.
const accentMinAreaFraction = 0.004

// accentAreaKnee is the area fraction at which the multiplicative area weight
// reaches ~63% of its maximum (the "knee" of the sqrt curve). Colors at this
// fraction or above are only mildly penalised; colors far below it are heavily
// penalised. Set to ~2% so a 2%-coverage accent has a weight of ~1.0 and a
// 0.4%-coverage micro-logo has a weight of ~0.45.
const accentAreaKnee = 0.02

// mostVibrantAccent finds a recurring saturated colour independently of the
// k-means clusters. Quantising samples into RGB buckets means antialiased text
// and small shapes contribute to one candidate instead of being treated as
// unrelated single pixels.
//
// Scoring uses a multiplicative area weight instead of an additive bonus so
// that a tiny, ultra-saturated cluster (e.g. a red logo on a muted cover)
// cannot simply overpower a larger legitimate accent through raw saturation.
// The weight follows a sqrt curve that is near-1 for colours covering ≥2% of
// pixels and falls sharply for rarer clusters, completely excluding anything
// below accentMinAreaFraction.
func mostVibrantAccent(src image.Image) (color.RGBA, bool) {
	sample := downsample(src, accentSampleSize, accentSampleSize)
	total := sample.Bounds().Dx() * sample.Bounds().Dy()
	minCount := max(int(math.Round(float64(total)*accentMinAreaFraction)), 4)

	type bucket struct {
		sumR, sumG, sumB int
		count            int
	}
	buckets := make(map[uint16]bucket)

	for y := sample.Bounds().Min.Y; y < sample.Bounds().Max.Y; y++ {
		for x := sample.Bounds().Min.X; x < sample.Bounds().Max.X; x++ {
			r, g, b, a := sample.At(x, y).RGBA()
			if a < 0x8000 {
				continue
			}
			pixel := color.RGBA{R: uint8(r >> 8), G: uint8(g >> 8), B: uint8(b >> 8), A: 0xFF}
			if vibrance(pixel) < 0.35 {
				continue
			}

			// 32-value channels absorb antialiasing/compression variation.
			key := uint16(pixel.R>>5)<<10 | uint16(pixel.G>>5)<<5 | uint16(pixel.B>>5)
			entry := buckets[key]
			entry.sumR += int(pixel.R)
			entry.sumG += int(pixel.G)
			entry.sumB += int(pixel.B)
			entry.count++
			buckets[key] = entry
		}
	}

	var best color.RGBA
	bestScore := -1.0
	for _, entry := range buckets {
		if entry.count < minCount {
			continue
		}
		candidate := color.RGBA{
			R: uint8(entry.sumR / entry.count),
			G: uint8(entry.sumG / entry.count),
			B: uint8(entry.sumB / entry.count),
			A: 0xFF,
		}

		// Area weight: sqrt(ratio / knee), clamped to [0, 1].
		// This gives a smooth, non-linear penalty to low-coverage buckets:
		//   ratio = 0.4% (min gate) → weight ≈ 0.45
		//   ratio = 1%              → weight ≈ 0.71
		//   ratio = 2% (knee)       → weight = 1.00
		//   ratio > 2%              → weight = 1.00 (clamped)
		//
		// Multiplying vibrance by areaWeight means a hyper-saturated micro-logo
		// (vibrance≈1.0, weight≈0.45) scores ≈0.45, while a moderately vibrant
		// but genuinely prevalent accent (vibrance≈0.7, weight≈0.80) scores ≈0.56.
		areaRatio := float64(entry.count) / float64(total)
		areaWeight := math.Min(1.0, math.Sqrt(areaRatio/accentAreaKnee))
		score := vibrance(candidate) * areaWeight

		if score > bestScore {
			best, bestScore = candidate, score
		}
	}

	return best, bestScore >= 0
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
