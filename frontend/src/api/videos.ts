import type {
  PlaybackResponse,
  VideoUploadResponse,
  Visibility,
} from "../types/video.types";
import apiClient from "./axiosInstance";

export interface VideoPatchRequest {
  title?: string;
  visibility?: Visibility;
}

export const uploadFile = async (file: File): Promise<VideoUploadResponse> => {
  const formData = new FormData();
  formData.append("file", file);

  const response = await apiClient.post<VideoUploadResponse>(
    "/videos",
    formData
  );

  return response.data;
};

export const fetchVideos = async (): Promise<VideoUploadResponse[]> => {
  const response = await apiClient.get("/videos/my-videos");
  return response.data;
};

/**
 * Asks the backend for signed, short-lived URLs to play a video. The access check (private videos are
 * owner/admin only) happens here; the returned URLs work without an Authorization header.
 */
export const getPlayback = async (id: string): Promise<PlaybackResponse> => {
  const response = await apiClient.get<PlaybackResponse>(
    `/videos/${id}/playback`
  );
  return response.data;
};

export const deleteVideo = async (id: string): Promise<void> => {
  await apiClient.delete(`/videos/${id}`);
};

export const updateVideo = async (
  id: string,
  patch: VideoPatchRequest
): Promise<VideoUploadResponse> => {
  const response = await apiClient.patch(`/videos/${id}`, patch);
  return response.data;
};
