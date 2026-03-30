package org.cloud.storage.util;

import com.madgag.gif.fmsware.AnimatedGifEncoder;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.bytedeco.javacv.FFmpegFrameGrabber;
import org.bytedeco.javacv.Frame;
import org.bytedeco.javacv.Java2DFrameConverter;
import org.springframework.stereotype.Component;

import java.awt.image.BufferedImage;
import java.io.*;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

@Slf4j
@Component
@RequiredArgsConstructor
public class GifGenerator {
    // 关键帧采样时间点：视频时长的百分比位置 (5% ~ 90%，均匀分布)
    private static final double[] KEYFRAME_PERCENTAGES = {0.05, 0.2, 0.5, 0.7, 0.9};

    // 每个场景的微动画帧数：3帧形成"渐入-定格-渐出"效果
    private static final int FRAMES_PER_SCENE = 3;

    // 帧率：GIF 播放帧率，用于控制整体播放速度
    private static final int FRAME_RATE = 24;

    // 微动画帧间步长：原视频中相邻帧的时间间隔（毫秒）
    private static final int FRAME_STEP_MS = 500;

    // 微动画内部帧延迟：每帧显示时长（毫秒），形成微动画效果
    private static final int INTRA_SCENE_DELAY_MS = 500;

    // 场景切换停顿时长：关键帧之间的定格时间（毫秒），让用户看清场景内容
    private static final int SCENE_TRANSITION_DELAY_MS = 2000;

    private static Path TEMP_DIR = Paths.get("tmp");

    static {
        try {
            Files.createDirectories(TEMP_DIR);
        } catch (IOException e) {
            // 创建失败，使用系统默认临时目录
            TEMP_DIR = Paths.get(System.getProperty("java.io.tmpdir"));
        }
    }

    public VideoInfo extractVideoInfo(String url) {
        // 使用 FFmpegFrameGrabber 代替命令行 ffprobe
        try (FFmpegFrameGrabber grabber = new FFmpegFrameGrabber(url)) {
            grabber.setOption("rw_timeout", "30000000");
            grabber.start();

            int width = grabber.getImageWidth();
            int height = grabber.getImageHeight();
            int durationSeconds = (int) (grabber.getLengthInTime() / 1_000_000L);

            grabber.stop();

            if (width <= 0 || height <= 0) {
                log.warn("Failed to extract valid dimensions for URL: {}", url);
                return null;
            }

            return new VideoInfo(width, height, durationSeconds);
        } catch (Exception e) {
            log.error("Failed to extract video info via JavaCV for URL: {}. Error: {}", url, e.getMessage());
            return null;
        }
    }

    /**
     * 从视频流生成预览GIF
     * 提取关键帧生成轻量级预览，适合视频缩略图展示
     *
     * @param videoFile 视频文件
     * @param videoInfo 视频信息（宽、高、时长秒数）
     * @return GIF字节数组
     */
    public byte[] generatePreviewGif(Path videoFile, VideoInfo videoInfo) throws IOException {
        long startTime = System.currentTimeMillis();

        FFmpegFrameGrabber grabber = null;

        try (ByteArrayOutputStream outputStream = new ByteArrayOutputStream()) {
            grabber = new FFmpegFrameGrabber(videoFile.toFile());
            grabber.start(false);

            int width = videoInfo.width();
            int height = videoInfo.height();
            long totalDurationMicros = (long) videoInfo.durationSeconds() * 1_000_000L;

            AnimatedGifEncoder encoder = new AnimatedGifEncoder();
            encoder.setFrameRate(FRAME_RATE);
            encoder.setSize(width, height);
            encoder.setRepeat(0); // 无限循环
            encoder.setQuality(15); // 1-20，值越低质量越高
            encoder.start(outputStream);

            int totalFrames = generateGifFrames(grabber, encoder, totalDurationMicros);

            encoder.finish();

            byte[] gifData = outputStream.toByteArray();
            logGenerationStats(startTime, totalFrames, gifData.length, width, height);

            return gifData;

        } finally {
            if (grabber != null) {
                try {
                    grabber.stop();
                    grabber.release();
                } catch (Exception e) {
                    log.warn("Failed to release grabber resources", e);
                }
            }
        }
    }

    /**
     * 生成 GIF 帧序列
     *
     * @param grabber 视频帧抓取器
     * @param encoder GIF 编码器
     * @param totalDurationMicros 视频总时长（微秒）
     * @return 实际生成的帧数
     * @throws IOException 当帧抓取失败时抛出
     */
    private int generateGifFrames(
            FFmpegFrameGrabber grabber,
            AnimatedGifEncoder encoder,
            long totalDurationMicros) throws IOException {

        int totalFrames = 0;
        long frameStepMicros = FRAME_STEP_MS * 1000L; // 转换为微秒

        try (Java2DFrameConverter converter = new Java2DFrameConverter()) {
            for (int sceneIdx = 0; sceneIdx < KEYFRAME_PERCENTAGES.length; sceneIdx++) {
                double percentage = KEYFRAME_PERCENTAGES[sceneIdx];
                long sceneCenterMicros = (long) (totalDurationMicros * percentage);

                // 计算微动画时间窗口：以场景中心为基准，向前偏移一帧作为起始
                long windowStartMicros = Math.max(0, sceneCenterMicros - frameStepMicros);

                // 抓取 3 帧形成微动画
                for (int frameIdx = 0; frameIdx < FRAMES_PER_SCENE; frameIdx++) {
                    long targetMicros = windowStartMicros + (frameIdx * frameStepMicros);

                    // 边界保护：不超出视频总时长
                    if (targetMicros > totalDurationMicros) {
                        break;
                    }

                    Frame frame = grabFrameAt(grabber, targetMicros);
                    if (frame == null) {
                        log.warn("Dropped frame at scene {}% frame {}",
                                (int) (percentage * 100), frameIdx);
                        continue;
                    }

                    BufferedImage image = converter.convert(frame);
                    if (image == null) {
                        continue;
                    }

                    // 添加微动画帧，设置帧间延迟
                    encoder.setDelay(INTRA_SCENE_DELAY_MS);
                    encoder.addFrame(image);
                    totalFrames++;
                }

                // 设置场景切换停顿：最后一帧延长显示时间形成"定格"效果
                setSceneTransitionDelay(encoder, sceneIdx);
            }
        }

        return totalFrames;
    }

    /**
     * 在指定时间点抓取视频帧
     *
     * @param grabber 视频帧抓取器
     * @param timestampMicros 目标时间戳（微秒）
     * @return 抓取到的帧，失败返回 null
     * @throws IOException 当抓取失败时抛出
     */
    private Frame grabFrameAt(FFmpegFrameGrabber grabber, long timestampMicros) throws IOException {
        grabber.setVideoTimestamp(timestampMicros);
        Frame frame = grabber.grabImage();
        return (frame != null && frame.image != null) ? frame : null;
    }
    /**
     * 设置场景切换时的帧延迟
     * 策略：
     * - 非最后一个场景：停顿让用户看清内容
     * - 最后一个场景：停顿减半，形成流畅循环过渡
     *
     * @param encoder GIF 编码器
     * @param sceneIdx 当前场景索引
     */
    private void setSceneTransitionDelay(AnimatedGifEncoder encoder, int sceneIdx) {
        boolean isLastScene = (sceneIdx == KEYFRAME_PERCENTAGES.length - 1);
        int delayMs = isLastScene
                ? SCENE_TRANSITION_DELAY_MS / 2
                : SCENE_TRANSITION_DELAY_MS;
        encoder.setDelay(delayMs);
    }

    private void logGenerationStats(long startTime, int totalFrames,
                                    int dataLength, int width, int height) {
        long elapsed = System.currentTimeMillis() - startTime;
        String sizeMb = String.format("%.2f", dataLength / (1024.0 * 1024.0));

        log.info("GIF generated: {} scenes, {} frames, {} MB ({}x{}),  elapsed {} ms",
                KEYFRAME_PERCENTAGES.length, totalFrames, sizeMb, width, height, elapsed);
    }

    public record VideoInfo (int width, int height, int durationSeconds) {}
}
