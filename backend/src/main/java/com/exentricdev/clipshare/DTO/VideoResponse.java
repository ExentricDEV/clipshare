package com.exentricdev.clipshare.DTO;

public record VideoResponse(
        String id,
        String title,
        long size,
        boolean isProcessed
) {
}
