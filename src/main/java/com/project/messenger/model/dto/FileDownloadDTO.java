package com.project.messenger.model.dto;

import lombok.Data;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpHeaders;

@Data
public class FileDownloadDTO {
    private final Resource resource;
    private final String contentType;
    private final HttpHeaders headers;

    public FileDownloadDTO(Resource resource, String contentType, HttpHeaders headers) {
        this.resource = resource;
        this.contentType = contentType;
        this.headers = headers;
    }
}
