package com.exentricdev.clipshare.DTO;

import com.exentricdev.clipshare.entity.Visibility;

public record VideoPatch(
        String title,
        Visibility visibility
) {
}
