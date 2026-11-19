import {
  Box,
  Button,
  LinearProgress,
  MenuItem,
  Select,
  TextField,
  Tooltip,
  Typography,
} from "@mui/material";
import { AlertTriangle, Link2, Play, Trash2 } from "lucide-react";
import React, { useEffect, useState } from "react";
import { deleteVideo, updateVideo } from "../api/videos";
import { AxiosError } from "axios";
import type { VideoUploadResponse, Visibility } from "../types/video.types";

export interface VideoCardProps {
  video: VideoUploadResponse;
  onChange?: (video: VideoUploadResponse) => void;
  onDelete?: () => void;
}

const VISIBILITY_LABELS: Record<Visibility, string> = {
  PRIVATE: "Private",
  UNLISTED: "Unlisted",
  PUBLIC: "Public",
};

const VISIBILITY_HINTS: Record<Visibility, string> = {
  PRIVATE: "Only you can watch",
  UNLISTED: "Anyone with the link can watch",
  PUBLIC: "Anyone can watch",
};

export const VideoCard: React.FC<VideoCardProps> = ({
  video,
  onChange,
  onDelete,
}) => {
  const { id: videoId, status, progress } = video;
  const ready = status === "READY";
  const [localTitle, setLocalTitle] = useState(video.title);
  const [savedTitle, setSavedTitle] = useState(video.title);
  const [isDeleting, setIsDeleting] = useState(false);
  const [titleError, setTitleError] = useState<string | null>(null);
  const [copied, setCopied] = useState(false);

  useEffect(() => {
    if (!copied) return;
    const t = setTimeout(() => setCopied(false), 1500);
    return () => clearTimeout(t);
  }, [copied]);

  const handleTitleBlur = async () => {
    if (localTitle === savedTitle) return;
    try {
      const updated = await updateVideo(videoId, { title: localTitle });
      setSavedTitle(localTitle);
      setTitleError(null);
      onChange?.(updated);
    } catch (error) {
      console.error("Failed to update title:", error);
      if ((error as AxiosError).response?.status === 400) {
        setTitleError("Title is too long or invalid");
        setLocalTitle(savedTitle);
      } else {
        setTitleError("Failed to update title");
      }
    }
  };

  const handleVisibility = async (visibility: Visibility) => {
    try {
      onChange?.(await updateVideo(videoId, { visibility }));
    } catch (error) {
      console.error("Failed to update visibility:", error);
    }
  };

  const handleDelete = async () => {
    if (!window.confirm("Are you sure you want to delete this video?")) return;
    setIsDeleting(true);
    try {
      await deleteVideo(videoId);
      onDelete?.();
    } catch (error) {
      console.error("Failed to delete video:", error);
      setIsDeleting(false);
    }
  };

  const qualities = video.renditions.length
    ? `${video.renditions.map((h) => `${h}p`).join(" · ")}`
    : null;

  return (
    <Box
      sx={{
        borderRadius: 2,
        overflow: "hidden",
        border: "1px solid #ddd",
        boxShadow: "0 1px 4px rgba(0,0,0,0.08)",
        width: "100%",
        maxWidth: 400,
        backgroundColor: "#fff",
      }}
    >
      <Box
        sx={{
          width: "100%",
          paddingTop: "56.25%", // 16:9
          position: "relative",
          backgroundColor: ready ? "#dcdcdc" : "#000",
          cursor: ready ? "pointer" : "default",
          "&:hover .play-overlay": { opacity: ready ? 1 : 0 },
        }}
        onClick={() => {
          if (ready) window.open(`${window.location.origin}/video/${videoId}`, "_blank");
        }}
      >
        {!ready && (
          <Box
            sx={{
              position: "absolute",
              inset: 0,
              display: "flex",
              flexDirection: "column",
              alignItems: "center",
              justifyContent: "center",
              gap: 1.5,
              px: 4,
              zIndex: 2,
              color: "white",
            }}
          >
            {status === "FAILED" ? (
              <>
                <AlertTriangle size={28} color="#ff8a80" />
                <Typography variant="body2" sx={{ fontWeight: "bold" }}>
                  Processing failed
                </Typography>
                {video.failureReason && (
                  <Typography variant="caption" sx={{ opacity: 0.75, textAlign: "center" }}>
                    {video.failureReason}
                  </Typography>
                )}
              </>
            ) : (
              <>
                <Typography variant="body2" sx={{ fontWeight: "bold" }}>
                  {status === "PENDING" ? "Waiting for a worker…" : `Processing… ${progress}%`}
                </Typography>
                <LinearProgress
                  variant={status === "PENDING" ? "indeterminate" : "determinate"}
                  value={progress}
                  sx={{ width: "100%", height: 6, borderRadius: 3 }}
                />
              </>
            )}
          </Box>
        )}
        {ready && (
          <>
            <Box
              className="play-overlay"
              sx={{
                position: "absolute",
                inset: 0,
                backgroundColor: "rgba(0, 0, 0, 0.3)",
                display: "flex",
                alignItems: "center",
                justifyContent: "center",
                opacity: 0,
                transition: "opacity 0.2s ease-in-out",
                zIndex: 1,
              }}
            >
              <Box
                sx={{
                  backgroundColor: "rgba(0, 0, 0, 0.6)",
                  borderRadius: "50%",
                  padding: 1.5,
                  display: "flex",
                  color: "white",
                }}
              >
                <Play size={32} fill="white" />
              </Box>
            </Box>
            {video.posterUrl && (
              <Box
                component="img"
                src={video.posterUrl}
                alt=""
                sx={{
                  position: "absolute",
                  inset: 0,
                  width: "100%",
                  height: "100%",
                  objectFit: "cover",
                  display: "block",
                }}
              />
            )}
            {qualities && (
              <Typography
                variant="caption"
                sx={{
                  position: "absolute",
                  right: 8,
                  bottom: 6,
                  px: 0.75,
                  borderRadius: 1,
                  color: "white",
                  backgroundColor: "rgba(0,0,0,0.6)",
                  zIndex: 1,
                }}
              >
                {qualities}
              </Typography>
            )}
          </>
        )}
      </Box>
      <Box
        sx={{
          p: 0.8,
          backgroundColor: "#f7f7f7",
          borderTop: "1px solid #e2e2e2",
          display: "flex",
          flexDirection: "row",
          alignItems: "center",
          gap: 1,
        }}
      >
        <TextField
          value={localTitle}
          onChange={(e) => {
            setLocalTitle(e.target.value);
            if (titleError) setTitleError(null);
          }}
          onBlur={handleTitleBlur}
          error={!!titleError}
          helperText={titleError}
          variant="outlined"
          size="small"
          fullWidth
          sx={{
            "& .MuiOutlinedInput-root": {
              borderRadius: 1.5,
              backgroundColor: "transparent",
              transition: "all 0.2s ease",
              "& fieldset": { borderColor: "transparent" },
              "&:hover": {
                backgroundColor: "#fff",
                "& fieldset": { borderColor: "rgba(0, 0, 0, 0.23)" },
              },
              "&.Mui-focused": {
                backgroundColor: "#fff",
                "& fieldset": { borderColor: "#1976d2" },
              },
            },
            input: { paddingY: "4px", fontSize: "0.9rem", fontWeight: 600 },
          }}
        />
        <Tooltip title={VISIBILITY_HINTS[video.visibility]}>
          <Select
            size="small"
            value={video.visibility}
            onChange={(e) => handleVisibility(e.target.value as Visibility)}
            sx={{ fontSize: "0.8rem", height: 30, minWidth: 96, backgroundColor: "#fff" }}
          >
            {(Object.keys(VISIBILITY_LABELS) as Visibility[]).map((v) => (
              <MenuItem key={v} value={v} sx={{ fontSize: "0.85rem" }}>
                {VISIBILITY_LABELS[v]}
              </MenuItem>
            ))}
          </Select>
        </Tooltip>
        <Tooltip
          title={
            copied
              ? "Copied!"
              : video.visibility === "PRIVATE"
                ? "Private: only you can open this link"
                : "Copy link"
          }
        >
          <Button
            size="small"
            sx={{
              minWidth: "30px",
              height: "30px",
              padding: 0,
              textTransform: "none",
              borderRadius: 1.5,
              backgroundColor: "#fff",
              border: "1px solid #ddd",
              color: "#555",
              "&:hover": { backgroundColor: "#f0f0f0" },
            }}
            onClick={() => {
              navigator.clipboard
                .writeText(`${window.location.origin}/video/${videoId}`)
                .then(() => setCopied(true))
                .catch((err) => console.error("Failed to copy video link: ", err));
            }}
          >
            <Link2 size={16} />
          </Button>
        </Tooltip>
        <Button
          size="small"
          disabled={isDeleting}
          sx={{
            minWidth: "30px",
            height: "30px",
            padding: 0,
            textTransform: "none",
            borderRadius: 1.5,
            backgroundColor: "#fff",
            border: "1px solid #ddd",
            color: "#d32f2f",
            "&:hover": { backgroundColor: "#ffebee", borderColor: "#d32f2f" },
          }}
          onClick={handleDelete}
        >
          <Trash2 size={16} />
        </Button>
      </Box>
    </Box>
  );
};

export default VideoCard;
