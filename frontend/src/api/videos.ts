import type { VideoUploadResponse } from "../types/video.types";
import apiClient from "./axiosInstance";

export interface VideoPatchRequest {
  title?: string;
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

export const getVideoStreamUrl = (id: string): string => {
  return `${apiClient.defaults.baseURL}/videos/${id}`;
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
