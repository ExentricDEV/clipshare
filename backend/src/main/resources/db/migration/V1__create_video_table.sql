CREATE TABLE video (
    id VARCHAR(255) PRIMARY KEY,
    uploader_id VARCHAR(255) NOT NULL,
    title VARCHAR(255) NOT NULL,
    url VARCHAR(255),
    is_processed BOOLEAN NOT NULL DEFAULT FALSE,
    stored_file_name VARCHAR(255) NOT NULL,
    size BIGINT NOT NULL,
    content_type VARCHAR(255) NOT NULL,
    uploaded_at TIMESTAMP WITH TIME ZONE NOT NULL
);

CREATE INDEX idx_video_uploader_id ON video(uploader_id);
CREATE INDEX idx_video_is_processed ON video(is_processed);
CREATE INDEX idx_video_uploaded_at ON video(uploaded_at);