package org.cloud.storage.service;

import org.cloud.storage.dto.MediaCoverDTO;
import org.cloud.storage.dto.PreviewDTO;

import java.util.List;
import java.util.UUID;

public interface PreviewService {
    List<MediaCoverDTO> getMediaCovers(List<String> fileHashes, UUID userId);

    List<PreviewDTO> getPreviews(List<UUID> fileIds, UUID userId);
}
