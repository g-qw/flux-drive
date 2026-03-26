package org.cloud.fs.service;

import org.cloud.fs.dto.ShareContent;
import org.cloud.fs.dto.ShareCreation;
import org.cloud.fs.dto.ShareDetailRequest;
import org.cloud.fs.dto.ShareView;
import org.cloud.fs.entity.ResourceShare;

import java.util.List;
import java.util.UUID;

public interface SpaceShareService {
    /**
     * 创建分享
     */
    ResourceShare createShare(ShareCreation dto, UUID userId);

    /**
     * 获取用户发布的分享
     */
    List<ShareView> getOwnedShares(UUID userId);

    /**
     * 公共访问分享
     */
    ShareView getShareViewByCode(String code);

    /**
     * 获取分享内容
     */
    ShareContent getShareContent(ShareDetailRequest request, UUID userId);

}
