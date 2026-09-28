package com.sahha.file.entity;

/** LEGACY grants are intentionally unusable after migration; callers request a fresh token. */
public enum DownloadAccessScope { LEGACY, OWN, SELECTED, SHARED_CARE }
