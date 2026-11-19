export type VideoStatus = "PENDING" | "PROCESSING" | "READY" | "FAILED";
export type Visibility = "PRIVATE" | "UNLISTED" | "PUBLIC";

export interface VideoUploadResponse {
  id: string;
  title: string;
  size: number;
  isProcessed: boolean;
  status: VideoStatus;
  /** 0-100 while transcoding */
  progress: number;
  failureReason: string | null;
  visibility: Visibility;
  durationSeconds: number | null;
  /** rendition heights, highest first, e.g. [1080, 720, 480] */
  renditions: number[];
  /** short-lived signed thumbnail URL, null until READY */
  posterUrl: string | null;
}

export interface VideoProgressEvent {
  id: string;
  progress: number;
}

export interface PlaybackResponse {
  id: string;
  title: string;
  /** signed HLS master playlist (or MP4 for videos processed before HLS) */
  url: string;
  type: "hls" | "mp4";
  posterUrl: string | null;
}
