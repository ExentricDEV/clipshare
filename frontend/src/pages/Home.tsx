import { useState, useCallback, useEffect } from "react";
import { useDropzone } from "react-dropzone";
import {
  Box,
  Button,
  Typography,
  Grid,
  AppBar,
  Toolbar,
  CircularProgress,
  Snackbar,
  Alert,
} from "@mui/material";
import { Upload, LogOut, Film } from "lucide-react";

import { blue, grey } from "@mui/material/colors";
import apiClient from "../api/axiosInstance";
import { uploadFile, fetchVideos } from "../api/videos";
import VideoCard from "../components/VideoCard";
import type {
  VideoProgressEvent,
  VideoUploadResponse,
} from "../types/video.types";
import keycloak, { freshToken } from "../keycloak";
import { fetchEventSource } from "@microsoft/fetch-event-source";
import { AxiosError } from "axios";

/** Thrown from the SSE handlers to stop fetch-event-source's own retry loop; we reconnect ourselves. */
class StreamClosed extends Error {}

function Home() {
  const [videos, setVideos] = useState<VideoUploadResponse[]>([]);
  const [isUploading, setIsUploading] = useState(false);
  const [isLoadingVideos, setIsLoadingVideos] = useState(true);
  const [notification, setNotification] = useState<{
    open: boolean;
    message: string;
    severity: "success" | "error" | "info" | "warning";
  }>({
    open: false,
    message: "",
    severity: "info",
  });

  const handleCloseNotification = () => {
    setNotification({ ...notification, open: false });
  };

  const username =
    keycloak.tokenParsed?.given_name ||
    keycloak.tokenParsed?.preferred_username ||
    "User";

  const onDrop = useCallback(
    (acceptedFiles: File[]) => {
      if (isUploading) return;

      const file = acceptedFiles[0];
      if (!file) return;

      console.log("File dropped:", file);
      setIsUploading(true);

      uploadFile(file)
        .then((response: VideoUploadResponse) => {
          console.log("Upload response:", response);
          setVideos((prevVideos) => [response, ...prevVideos]);
          setNotification({
            open: true,
            message: "Video uploaded successfully!",
            severity: "success",
          });
        })
        .catch((error) => {
          console.error("Error uploading file:", error);
          let errorMessage = "Error uploading file";
          if (error instanceof AxiosError) {
            if (error.response) {
              // The request was made and the server responded with a status code
              // that falls out of the range of 2xx
              errorMessage =
                error.response.data ||
                error.response.statusText ||
                "Server error";
            } else if (error.request) {
              // The request was made but no response was received
              errorMessage = "No response from server";
            } else {
              // Something happened in setting up the request that triggered an Error
              errorMessage = error.message;
            }
          } else if (error instanceof Error) {
            errorMessage = error.message;
          }
          setNotification({
            open: true,
            message: errorMessage,
            severity: "error",
          });
        })
        .finally(() => {
          setIsUploading(false);
        });
    },
    [isUploading]
  );

  // Update the video list on initial render
  useEffect(() => {
    setIsLoadingVideos(true);
    fetchVideos()
      .then((fetchedVideos) => {
        // Log the fetched videos for debugging
        console.log("Fetched videos:", fetchedVideos);

        setVideos(fetchedVideos);
      })
      .catch((error) => {
        console.error("Error fetching videos:", error);
      })
      .finally(() => {
        setIsLoadingVideos(false);
      });
  }, []);

  // Live transcode updates over server-sent events.
  // The server closes each stream after 30 minutes, and access tokens only last 5, so every (re)connect
  // fetches a fresh token first. Reconnects back off from 1s to 30s while the server is unreachable.
  useEffect(() => {
    const abortController = new AbortController();
    let retryMs = 1000;
    let timer: ReturnType<typeof setTimeout> | undefined;

    const replaceVideo = (updated: VideoUploadResponse) =>
      setVideos((prev) => prev.map((v) => (v.id === updated.id ? updated : v)));

    const connect = async () => {
      if (abortController.signal.aborted) return;
      const token = await freshToken();
      try {
        await fetchEventSource(`${apiClient.defaults.baseURL}/videos/events`, {
          method: "GET",
          headers: { Authorization: `Bearer ${token}` },
          signal: abortController.signal,
          openWhenHidden: true,

          onopen: async (response) => {
            if (!response.ok) throw new StreamClosed(`SSE ${response.status}`);
            retryMs = 1000;
          },
          onmessage: (event) => {
            if (event.event === "video-progress") {
              const { id, progress }: VideoProgressEvent = JSON.parse(event.data);
              setVideos((prev) =>
                prev.map((v) =>
                  v.id === id && progress > v.progress
                    ? { ...v, status: "PROCESSING", progress }
                    : v
                )
              );
            } else if (event.event === "video-processed") {
              replaceVideo(JSON.parse(event.data));
            } else if (event.event === "video-failed") {
              const failed: VideoUploadResponse = JSON.parse(event.data);
              replaceVideo(failed);
              setNotification({
                open: true,
                message: `"${failed.title}" could not be processed`,
                severity: "error",
              });
            }
          },
          onclose: () => {
            throw new StreamClosed("closed by server");
          },
          onerror: (err) => {
            throw err; // stop the library's retry; reconnect below with a fresh token
          },
        });
      } catch {
        // fall through to reconnect
      }
      if (abortController.signal.aborted) return;
      // re-sync in case events were missed while disconnected
      fetchVideos().then(setVideos).catch(() => {});
      timer = setTimeout(connect, retryMs);
      retryMs = Math.min(retryMs * 2, 30_000);
    };

    connect();

    return () => {
      abortController.abort();
      if (timer) clearTimeout(timer);
    };
  }, []);

  const { getRootProps, getInputProps, isDragActive } = useDropzone({
    onDrop,
  });

  return (
    <Box sx={{ flexGrow: 1 }}>
      <Snackbar
        open={notification.open}
        autoHideDuration={6000}
        onClose={handleCloseNotification}
        anchorOrigin={{ vertical: "bottom", horizontal: "center" }}
      >
        <Alert
          onClose={handleCloseNotification}
          severity={notification.severity}
          sx={{ width: "100%" }}
        >
          {notification.message}
        </Alert>
      </Snackbar>
      <AppBar position="static" sx={{ backgroundColor: blue[800] }}>
        <Toolbar>
          <Typography
            variant="h6"
            component="div"
            sx={{ flexGrow: 1, fontWeight: "bold" }}
          >
            ClipShare
          </Typography>
          <Typography variant="body1" sx={{ mr: 2, fontWeight: 500 }}>
            Welcome, {username}
          </Typography>
          <Button
            color="inherit"
            onClick={() => keycloak.logout()}
            startIcon={<LogOut size={18} />}
            sx={{ textTransform: "none" }}
          >
            Logout
          </Button>
        </Toolbar>
      </AppBar>
      <Box
        sx={{
          maxWidth: "1280px",
          margin: "0 auto",
          padding: "2rem",
          textAlign: "center",
        }}
      >
        <Box
          sx={{
            display: "flex",
            flexDirection: "column",
            alignItems: "center",
            maxWidth: "600px",
            mx: "auto",
            pt: 4,
            gap: 4,
          }}
        >
          <Box
            {...getRootProps()}
            component="section"
            sx={{
              width: "600px",
              height: "200px",
              border: "2px dashed",
              borderColor: isDragActive ? blue[500] : grey[300],
              borderRadius: 4,
              backgroundColor: isDragActive ? blue[50] : "#fff",
              transition: "all 0.2s ease-in-out",
              textAlign: "center",
              display: "flex",
              flexDirection: "column",
              alignItems: "center",
              justifyContent: "center",
              cursor: "pointer",
            }}
          >
            <input {...getInputProps()} />
            {isUploading ? (
              <Box
                sx={{
                  display: "flex",
                  flexDirection: "column",
                  alignItems: "center",
                }}
              >
                <CircularProgress size={40} />
                <Typography variant="body2" sx={{ mt: 2, color: grey[600] }}>
                  Uploading...
                </Typography>
              </Box>
            ) : (
              <>
                <Box
                  sx={{
                    width: 48,
                    height: 48,
                    borderRadius: "50%",
                    backgroundColor: isDragActive ? blue[500] : grey[100],
                    display: "flex",
                    alignItems: "center",
                    justifyContent: "center",
                    transition: "background-color 0.2s ease-in-out",
                  }}
                >
                  <Upload
                    size={28}
                    color={isDragActive ? "white" : grey[500]}
                    style={{ transition: "stroke 0.2s ease-in-out" }}
                  />
                </Box>
                <Typography
                  variant="h6"
                  sx={{ mt: 2, color: isDragActive ? blue[700] : grey[700] }}
                >
                  {isDragActive
                    ? "Drop the files here..."
                    : "Drag and drop files here, or click to select files"}
                </Typography>
              </>
            )}
          </Box>
        </Box>
        {isLoadingVideos ? (
          <Box sx={{ display: "flex", justifyContent: "center", mt: 8 }}>
            <CircularProgress />
          </Box>
        ) : videos.length === 0 ? (
          <Box
            sx={{
              display: "flex",
              flexDirection: "column",
              alignItems: "center",
              mt: 8,
              opacity: 0.7,
            }}
          >
            <Film size={64} color={grey[400]} />
            <Typography variant="h6" sx={{ mt: 2, color: grey[600] }}>
              No clips yet
            </Typography>
            <Typography variant="body2" sx={{ color: grey[500] }}>
              Upload a video to get started
            </Typography>
          </Box>
        ) : (
          <Grid
            container
            sx={{
              mt: 3,
              display: "grid",
              gap: 1,
              gridTemplateColumns: "repeat(auto-fill, minmax(400px, 1fr))",
              justifyItems: "center",
            }}
          >
            {videos.map((video) => (
              <VideoCard
                key={video.id}
                video={video}
                onChange={(updated) =>
                  setVideos((prev) =>
                    prev.map((v) => (v.id === updated.id ? updated : v))
                  )
                }
                onDelete={() => {
                  setVideos((prevVideos) =>
                    prevVideos.filter((v) => v.id !== video.id)
                  );
                }}
              />
            ))}
          </Grid>
        )}
      </Box>
    </Box>
  );
}

export default Home;
