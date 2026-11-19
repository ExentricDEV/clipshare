package com.exentricdev.clipshare.entity;

public enum Visibility {
    /** only the uploader and admins */
    PRIVATE,
    /** anyone with the link, but never listed */
    UNLISTED,
    /** anyone */
    PUBLIC
}
