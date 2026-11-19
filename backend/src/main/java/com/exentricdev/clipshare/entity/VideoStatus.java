package com.exentricdev.clipshare.entity;

public enum VideoStatus {
    /** uploaded, waiting for a worker */
    PENDING,
    /** a worker is transcoding it (progress is being reported) */
    PROCESSING,
    /** HLS ladder published, playable */
    READY,
    /** retries exhausted; the job was dead-lettered */
    FAILED;

    public boolean isActive() {
        return this == PENDING || this == PROCESSING;
    }
}
