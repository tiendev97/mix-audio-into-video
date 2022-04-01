package com.tienducky.mixaudioandvideo.models;

import java.io.Serializable;

public class MediaItem implements Serializable {
    private String filePath;
    private String fileName;

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

    @Override
    public String toString() {
        return "MediaItem{" +
                "filePath='" + filePath + '\'' +
                ", fileName='" + fileName + '\'' +
                '}';
    }
}