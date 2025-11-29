import { Box, Button, TextField, Typography } from "@mui/material";
import { Link2, Play, Trash2 } from "lucide-react";
import React, { useState } from "react";
import { deleteVideo, updateVideo } from "../api/videos";
import { AxiosError } from "axios";

export interface VideoCardProps {
  videoId: string;
  videoUrl: string;
  title: string;
  processed: boolean;
  onTitleChange?: (value: string) => void;
  onDelete?: () => void;
}

export const VideoCard: React.FC<VideoCardProps> = ({
  videoId,
  videoUrl,
  title,
  processed,
  onTitleChange,
  onDelete,
}) => {
  const [localTitle, setLocalTitle] = useState(title);
  const [savedTitle, setSavedTitle] = useState(title);
  const [isDeleting, setIsDeleting] = useState(false);
  const [titleError, setTitleError] = useState<string | null>(null);

  const handleTitleChange = (e: React.ChangeEvent<HTMLInputElement>) => {
    setLocalTitle(e.target.value);
    onTitleChange?.(e.target.value);
    if (titleError) setTitleError(null);
  };

  const handleTitleBlur = async () => {
    if (localTitle !== savedTitle) {
      try {
        await updateVideo(videoId, { title: localTitle });
        setSavedTitle(localTitle);
        setTitleError(null);
      } catch (error) {
        console.error("Failed to update title:", error);

        const axiosError = error as AxiosError;

        if (axiosError.response?.status === 400) {
          setTitleError("Title is too long or invalid");
          setLocalTitle(savedTitle);
        } else {
          setTitleError("Failed to update title");
        }
      }
    }
  };

  const handleDelete = async () => {
    if (window.confirm("Are you sure you want to delete this video?")) {
      setIsDeleting(true);
      try {
        await deleteVideo(videoId);
        onDelete?.();
      } catch (error) {
        console.error("Failed to delete video:", error);
        setIsDeleting(false);
      }
    }
  };

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
          paddingTop: "47.62%", // 2.1:1 aspect ratio
          position: "relative",
          backgroundColor: processed ? "#dcdcdc" : "#000",
          cursor: processed ? "pointer" : "default",
          "&:hover .play-overlay": {
            opacity: processed ? 1 : 0,
          },
        }}
        onClick={() => {
          if (processed) {
            window.open(`${window.location.origin}/video/${videoId}`, "_blank");
          }
        }}
      >
        {!processed && (
          <Box
            sx={{
              position: "absolute",
              top: 0,
              left: 0,
              width: "100%",
              height: "100%",
              display: "flex",
              alignItems: "center",
              justifyContent: "center",
              zIndex: 2,
            }}
          >
            <Typography
              variant="body1"
              sx={{ color: "white", fontWeight: "bold" }}
            >
              Processing...
            </Typography>
          </Box>
        )}
        {processed && (
          <>
            <Box
              className="play-overlay"
              sx={{
                position: "absolute",
                top: 0,
                left: 0,
                width: "100%",
                height: "100%",
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
            <Box
              component="video"
              src={videoUrl}
              preload="metadata"
              sx={{
                position: "absolute",
                top: 0,
                left: 0,
                width: "100%",
                height: "100%",
                objectFit: "cover",
                display: "block",
              }}
            />
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
          onChange={handleTitleChange}
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
              "& fieldset": {
                borderColor: "transparent",
              },
              "&:hover": {
                backgroundColor: "#fff",
                "& fieldset": {
                  borderColor: "rgba(0, 0, 0, 0.23)",
                },
              },
              "&.Mui-focused": {
                backgroundColor: "#fff",
                "& fieldset": {
                  borderColor: "#1976d2",
                },
              },
            },
            input: {
              paddingY: "4px",
              fontSize: "0.9rem",
              fontWeight: 600,
            },
          }}
        />
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
            const videoLink = `${window.location.origin}/video/${videoId}`;

            try {
              navigator.clipboard.writeText(videoLink);
            } catch (err) {
              console.error("Failed to copy video link: ", err);
            }
          }}
        >
          <Link2 size={16} />
        </Button>
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
