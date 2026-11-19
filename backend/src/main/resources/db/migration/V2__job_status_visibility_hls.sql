-- Explicit job state instead of a single boolean, so a failed transcode no longer looks "still processing"
-- forever (and no longer blocks the user from uploading again).
ALTER TABLE video ADD COLUMN status VARCHAR(20) NOT NULL DEFAULT 'PENDING';
ALTER TABLE video ADD COLUMN progress INT NOT NULL DEFAULT 0;
ALTER TABLE video ADD COLUMN failure_reason VARCHAR(500);

-- Who can watch: PRIVATE (owner and admins), UNLISTED (anyone with the link), PUBLIC
ALTER TABLE video ADD COLUMN visibility VARCHAR(20) NOT NULL DEFAULT 'PRIVATE';

-- HLS output details
ALTER TABLE video ADD COLUMN poster_path VARCHAR(255);
ALTER TABLE video ADD COLUMN duration_seconds DOUBLE PRECISION;
ALTER TABLE video ADD COLUMN renditions VARCHAR(100);

-- Videos processed before this migration were single MP4 files that anyone could stream:
-- mark them READY and keep them watchable by link.
UPDATE video SET status = 'READY', progress = 100, visibility = 'UNLISTED' WHERE is_processed = TRUE;

CREATE INDEX idx_video_uploader_status ON video(uploader_id, status);
