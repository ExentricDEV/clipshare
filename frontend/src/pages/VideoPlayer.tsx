import { useParams } from "react-router";
import ReactPlayer from "react-player";
import {
  MediaController,
  MediaControlBar,
  MediaTimeRange,
  MediaTimeDisplay,
  MediaVolumeRange,
  MediaPlaybackRateButton,
  MediaPlayButton,
  MediaSeekBackwardButton,
  MediaSeekForwardButton,
  MediaMuteButton,
  MediaFullscreenButton,
} from "media-chrome/react";
import { Box } from "@mui/material";
import { getVideoStreamUrl } from "../api/videos";

export default function VideoPlayer() {
  const { id } = useParams();

  return (
    <Box
      sx={{
        maxWidth: "1000px",
        margin: "2rem auto",
        padding: "1rem",
      }}
    >
      <MediaController
        style={
          {
            width: "100%",
            aspectRatio: "16/9",
            borderRadius: "12px",
            overflow: "hidden",
            "--media-control-background": "transparent",
            "--media-control-hover-background": "rgba(255, 255, 255, 0.1)",
          } as any
        }
      >
        <ReactPlayer
          slot="media"
          src={getVideoStreamUrl(id!)}
          controls={false}
          width="100%"
          height="100%"
        />
        <MediaControlBar
          style={{
            display: "flex",
            flexDirection: "column",
            justifyContent: "flex-end",
            background:
              "linear-gradient(to top, rgba(0,0,0,0.7) 0%, transparent 100%)",
            padding: "0 20px 10px 20px",
            width: "100%",
          }}
        >
          <MediaTimeRange
            style={
              {
                width: "100%",
                height: "8px",
                padding: 0,
                marginBottom: "0px",
                backgroundColor: "transparent",
                "--media-range-track-height": "3px",
                "--media-range-thumb-height": "12px",
                "--media-range-thumb-width": "12px",
                "--media-range-track-background": "rgba(255, 255, 255, 0.3)",
                "--media-range-bar-color": "#3ea6ff",
              } as any
            }
          />
          <Box
            sx={{
              display: "flex",
              width: "100%",
              alignItems: "center",
              justifyContent: "space-between",
            }}
          >
            <Box sx={{ display: "flex", alignItems: "center", gap: 0.5 }}>
              <MediaPlayButton style={{ height: "44px", width: "44px" }} />
              <MediaSeekBackwardButton
                seekOffset={10}
                style={{ height: "44px", width: "44px" }}
              />
              <MediaSeekForwardButton
                seekOffset={10}
                style={{ height: "44px", width: "44px" }}
              />

              {/* Volume Control Group */}
              <Box
                sx={{
                  display: "flex",
                  alignItems: "center",
                  "&:hover .volume-container": {
                    width: "80px",
                    opacity: 1,
                    ml: 1,
                  },
                }}
              >
                <MediaMuteButton style={{ height: "44px", width: "44px" }} />
                <Box
                  className="volume-container"
                  sx={{
                    width: 0,
                    opacity: 0,
                    overflow: "hidden",
                    transition: "all 0.3s ease",
                    display: "flex",
                    alignItems: "center",
                  }}
                >
                  <MediaVolumeRange
                    style={
                      {
                        width: "80px",
                        "--media-range-track-height": "3px",
                        "--media-range-thumb-height": "10px",
                        "--media-range-thumb-width": "10px",
                        "--media-control-hover-background": "transparent",
                      } as any
                    }
                  />
                </Box>
              </Box>

              <MediaTimeDisplay
                showDuration
                style={{ marginLeft: "8px", fontSize: "13px" }}
              />
            </Box>
            <Box sx={{ display: "flex", alignItems: "center" }}>
              <MediaPlaybackRateButton
                style={{ height: "44px", width: "44px" }}
              />
              <MediaFullscreenButton
                style={{ height: "44px", width: "44px" }}
              />
            </Box>
          </Box>
        </MediaControlBar>
      </MediaController>
    </Box>
  );
}
