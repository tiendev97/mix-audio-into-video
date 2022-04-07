package com.samsung.mixaudioandvideo.models;

import java.io.Serializable;

public class MediaItem implements Serializable {
    private String filePath;
    private String fileName;
    private int mediaType;

    public String getFilePath() {
        return filePath;
    }

    public void setFilePath(String filePath) {
        this.filePath = filePath;
    }

    public String getFileName() {
        return fileName;
    }

    public void setFileName(String fileName) {
        this.fileName = fileName;
    }

    public int getMediaType() {
        return mediaType;
    }

    public void setMediaType(int mediaType) {
        this.mediaType = mediaType;
    }

    public boolean isVideo() {
        return mediaType == MediaType.VIDEO;
    }

    @Override
    public String toString() {
        return "MediaItem{" +
                "filePath='" + filePath + '\'' +
                ", fileName='" + fileName + '\'' +
                ", mediaType=" + mediaType +
                '}';
    }
}
