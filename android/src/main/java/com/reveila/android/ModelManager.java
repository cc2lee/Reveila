package com.reveila.android;

import android.content.Context;
import android.os.StatFs;

import com.reveila.tools.download.DownloadHandle;
import com.reveila.tools.download.DownloadProgress;
import com.reveila.tools.download.DownloadRequest;
import com.reveila.tools.download.FileDownloadCallback;
import com.reveila.tools.download.FileDownloadCallback_jvmKt;
import com.reveila.tools.download.ResumableDownloadManager;
import com.reveila.tools.download.TempCleanupReport;

import java.io.File;
import java.util.Collections;

/**
 * Manages the downloading, verification, and persistence of AI models
 * (GGUF / LiteRT) for the Sovereign Memory system, backed by the generic
 * {@link ResumableDownloadManager} contract.
 */
public class ModelManager {

    private static final String MODELS_DIR = "sovereign_models";
    private static final String TEMP_DIR = "reveila/temp/downloads";

    private final Context context;
    private final File modelDir;
    private final File tempDir;
    private final ResumableDownloadManager downloadManager;

    public interface DownloadCallback {
        void onProgress(int percentage);
        void onSuccess(File modelFile);
        void onError(Exception e);
    }

    public ModelManager(Context context) {
        this.context = context.getApplicationContext();
        this.modelDir = new File(this.context.getFilesDir(), MODELS_DIR);
        if (!this.modelDir.exists()) {
            this.modelDir.mkdirs();
        }
        this.tempDir = new File(this.context.getNoBackupFilesDir(), TEMP_DIR);
        if (!this.tempDir.exists()) {
            this.tempDir.mkdirs();
        }
        this.downloadManager = new ResumableDownloadManager(
                this.context.getFilesDir().getAbsolutePath(),
                this.tempDir.getAbsolutePath()
        );
        this.downloadManager.startPeriodicHousekeeping(3600L, 60L);
    }

    /**
     * Checks available device storage (in bytes) on the model staging partition.
     */
    public long checkAvailableStorageBytes() {
        try {
            return new StatFs(modelDir.getAbsolutePath()).getAvailableBytes();
        } catch (Exception e) {
            return downloadManager.checkAvailableStorage(modelDir.getAbsolutePath());
        }
    }

    /**
     * Downloads a model in the background, verifies storage and integrity, and persists it
     * in the app's internal scoped storage. Supports pause, resume, and cancel.
     */
    public DownloadHandle downloadModel(
            String modelUrl,
            String expectedSha256,
            String fileName,
            DownloadCallback callback
    ) {
        return downloadModel(modelUrl, expectedSha256, fileName, -1L, callback);
    }

    public DownloadHandle downloadModel(
            String modelUrl,
            String expectedSha256,
            String fileName,
            long expectedSizeBytes,
            DownloadCallback callback
    ) {
        File outputFile = new File(modelDir, fileName);
        DownloadRequest request = new DownloadRequest(
                fileName,
                modelUrl,
                Collections.emptyList(),
                outputFile.getAbsolutePath(),
                tempDir.getAbsolutePath(),
                expectedSha256 != null ? expectedSha256 : "",
                expectedSizeBytes,
                0L,
                Collections.emptyMap(),
                "Reveila-Android-ModelManager/1.0",
                30L,
                30L
        );
        return downloadManager.download(request, adaptCallback(callback));
    }

    public boolean pauseDownload(String fileName) {
        return downloadManager.pause(fileName);
    }

    public DownloadHandle resumeDownload(String fileName, DownloadCallback callback) {
        return downloadManager.resume(fileName, adaptCallback(callback));
    }

    public boolean cancelDownload(String fileName) {
        return downloadManager.cancel(fileName);
    }

    public DownloadProgress getProgress(String fileName) {
        return downloadManager.getProgress(fileName);
    }

    public TempCleanupReport runTempHousekeeping() {
        return downloadManager.runHousekeeping();
    }

    private com.reveila.tools.download.DownloadCallback adaptCallback(DownloadCallback callback) {
        if (callback == null) {
            return null;
        }
        return FileDownloadCallback_jvmKt.asDownloadCallback(new FileDownloadCallback() {
            @Override
            public void onProgress(int percentage) {
                callback.onProgress(percentage);
            }

            @Override
            public void onSuccess(File modelFile) {
                callback.onSuccess(modelFile);
            }

            @Override
            public void onError(Exception e) {
                callback.onError(e);
            }
        });
    }
}
