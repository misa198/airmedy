//go:build production

package audio

/*
#include <libavutil/log.h>
*/
import "C"

// Prod build: silence libav's own stderr logging (the "[Parsed_astats_2 @ ...]"
// / "Could not find codec parameters" lines analyzer.go's ffmpeg_analyze and the
// decoder emit directly, bypassing the Go logger). Dev build keeps the default
// level (see ffmpeg_log_dev.go) so those lines stay visible while iterating.
func init() {
	C.av_log_set_level(C.AV_LOG_QUIET)
}
