package org.cloud.storage.util;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import net.coobird.thumbnailator.Thumbnails;
import org.cloud.storage.dto.ThumbnailDTO;
import org.cloud.storage.dto.enums.ThumbnailFormat;
import org.springframework.stereotype.Component;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.*;

@Slf4j
@Component
@RequiredArgsConstructor
public class ThumbnailGenerator {
    // 默认输出质量 (0.0 - 1.0)
    private static final float DEFAULT_QUALITY = 0.8f;

    /**
     * 生成自适应缩略图
     *
     * @param inputStream  源图片输入流
     * @param maxWidth     最大宽度限制
     * @param maxHeight    最大高度限制
     * @param format       输出格式 (webp/jpeg/png)
     * @return 缩略图字节数组
     */
    public ThumbnailDTO generate(InputStream inputStream, int maxWidth, int maxHeight, ThumbnailFormat format) throws IOException {
        BufferedImage sourceImage = ImageIO.read(inputStream);
        if (sourceImage == null) {
            throw new IllegalArgumentException("Invalid image file");
        }

        int sourceWidth = sourceImage.getWidth();
        int sourceHeight = sourceImage.getHeight();
        int[] targetSize = calculateAdaptiveSize(sourceWidth, sourceHeight, maxWidth, maxHeight);

        ByteArrayOutputStream output = new ByteArrayOutputStream();

        Thumbnails.of(sourceImage)
                .size(targetSize[0], targetSize[1])
                .keepAspectRatio(true)
                .outputQuality(DEFAULT_QUALITY)
                .outputFormat(format.getExtension())
                .toOutputStream(output);

        sourceImage.flush();

        return new ThumbnailDTO(output.toByteArray(), targetSize[0], targetSize[1]);
    }

    /**
     * 计算自适应尺寸
     * 规则：保持比例，适应目标框，小图不放大
     */
    private int[] calculateAdaptiveSize(int sourceWidth, int sourceHeight, int maxWidth, int maxHeight) {
        // 小图不放大，直接返回原尺寸
        if (sourceWidth <= maxWidth && sourceHeight <= maxHeight) {
            return new int[]{sourceWidth, sourceHeight};
        }

        // 计算缩放比例，取较小值保持完整显示
        double scaleX = (double) maxWidth / sourceWidth;
        double scaleY = (double) maxHeight / sourceHeight;
        double scale = Math.min(scaleX, scaleY);

        int targetWidth = (int) (sourceWidth * scale);
        int targetHeight = (int) (sourceHeight * scale);

        return new int[]{targetWidth, targetHeight};
    }
}


