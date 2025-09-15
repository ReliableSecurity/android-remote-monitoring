package com.example.remotemonitor;

import android.Manifest;
import android.content.Context;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.ImageFormat;
import android.graphics.Matrix;
import android.graphics.SurfaceTexture;
import android.hardware.Camera;
import android.hardware.camera2.*;
import android.hardware.camera2.params.StreamConfigurationMap;
import android.media.Image;
import android.media.ImageReader;
import android.media.MediaRecorder;
import android.os.Build;
import android.os.Handler;
import android.os.HandlerThread;
import android.util.Base64;
import android.util.Log;
import android.util.Size;
import android.view.Surface;

import androidx.core.app.ActivityCompat;

import org.json.JSONException;
import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

/**
 * Универсальная камера система для Android 5.0+
 * Поддерживает как Camera API (старые устройства), так и Camera2 API (новые)
 */
public class CameraManager {
    
    private static final String TAG = "CameraManager";
    private Context context;
    
    // Camera API (для Android < 5.0 совместимости)
    private Camera legacyCamera;
    private int legacyCameraId = 0;
    
    // Camera2 API (для Android 5.0+)
    private android.hardware.camera2.CameraManager camera2Manager;
    private CameraDevice cameraDevice;
    private CameraCaptureSession captureSession;
    private ImageReader imageReader;
    private HandlerThread backgroundThread;
    private Handler backgroundHandler;
    
    // Состояние
    private boolean isCamera2Supported = false;
    private boolean isCameraOpen = false;
    private int currentCameraId = 0; // 0 - задняя, 1 - передняя
    private boolean isRecording = false;
    private MediaRecorder mediaRecorder;
    
    // Callbacks
    public interface CameraCallback {
        void onPhotoTaken(String base64Image);
        void onVideoFrame(String base64Frame);
        void onError(String error);
        void onCameraOpened();
        void onCameraClosed();
    }
    
    private CameraCallback callback;
    
    public CameraManager(Context context) {
        this.context = context;
        this.isCamera2Supported = Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP;
        
        if (isCamera2Supported) {
            camera2Manager = (android.hardware.camera2.CameraManager) 
                context.getSystemService(Context.CAMERA_SERVICE);
        }
    }
    
    public void setCallback(CameraCallback callback) {
        this.callback = callback;
    }
    
    /**
     * Проверка разрешений камеры
     */
    public boolean hasCameraPermission() {
        return ActivityCompat.checkSelfPermission(context, Manifest.permission.CAMERA) 
                == PackageManager.PERMISSION_GRANTED;
    }
    
    /**
     * Получение информации о доступных камерах
     */
    public JSONObject getCameraInfo() {
        JSONObject info = new JSONObject();
        
        try {
            info.put("camera2_supported", isCamera2Supported);
            info.put("sdk_version", Build.VERSION.SDK_INT);
            
            if (isCamera2Supported && camera2Manager != null) {
                String[] cameraIds = camera2Manager.getCameraIdList();
                info.put("camera_count", cameraIds.length);
                
                for (int i = 0; i < cameraIds.length; i++) {
                    CameraCharacteristics characteristics = 
                        camera2Manager.getCameraCharacteristics(cameraIds[i]);
                    
                    Integer facing = characteristics.get(CameraCharacteristics.LENS_FACING);
                    String facingStr = (facing == CameraCharacteristics.LENS_FACING_FRONT) ? 
                        "front" : "back";
                    
                    info.put("camera_" + i + "_facing", facingStr);
                    
                    StreamConfigurationMap map = characteristics.get(
                        CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP);
                    if (map != null) {
                        Size[] sizes = map.getOutputSizes(ImageFormat.JPEG);
                        if (sizes != null && sizes.length > 0) {
                            info.put("camera_" + i + "_max_resolution", 
                                sizes[0].getWidth() + "x" + sizes[0].getHeight());
                        }
                    }
                }
            } else {
                // Legacy Camera API
                int cameraCount = Camera.getNumberOfCameras();
                info.put("camera_count", cameraCount);
                
                for (int i = 0; i < cameraCount; i++) {
                    Camera.CameraInfo cameraInfo = new Camera.CameraInfo();
                    Camera.getCameraInfo(i, cameraInfo);
                    
                    String facing = (cameraInfo.facing == Camera.CameraInfo.CAMERA_FACING_FRONT) ? 
                        "front" : "back";
                    info.put("camera_" + i + "_facing", facing);
                }
            }
            
        } catch (Exception e) {
            Log.e(TAG, "Ошибка получения информации о камере: " + e.getMessage());
            try {
                info.put("error", e.getMessage());
            } catch (JSONException je) {}
        }
        
        return info;
    }
    
    /**
     * Открытие камеры
     */
    public void openCamera(int cameraId) {
        if (!hasCameraPermission()) {
            if (callback != null) {
                callback.onError("Нет разрешения на использование камеры");
            }
            return;
        }
        
        this.currentCameraId = cameraId;
        
        if (isCamera2Supported) {
            openCamera2(cameraId);
        } else {
            openLegacyCamera(cameraId);
        }
    }
    
    /**
     * Camera2 API (Android 5.0+)
     */
    private void openCamera2(int cameraId) {
        try {
            startBackgroundThread();
            
            String[] cameraIds = camera2Manager.getCameraIdList();
            if (cameraId >= cameraIds.length) {
                if (callback != null) {
                    callback.onError("Камера с ID " + cameraId + " не найдена");
                }
                return;
            }
            
            String selectedCameraId = cameraIds[cameraId];
            CameraCharacteristics characteristics = 
                camera2Manager.getCameraCharacteristics(selectedCameraId);
            
            StreamConfigurationMap map = characteristics.get(
                CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP);
            Size largest = Collections.max(Arrays.asList(map.getOutputSizes(ImageFormat.JPEG)),
                new CompareSizesByArea());
            
            imageReader = ImageReader.newInstance(largest.getWidth(), largest.getHeight(),
                ImageFormat.JPEG, 1);
            imageReader.setOnImageAvailableListener(onImageAvailableListener, backgroundHandler);
            
            if (ActivityCompat.checkSelfPermission(context, Manifest.permission.CAMERA) 
                != PackageManager.PERMISSION_GRANTED) {
                return;
            }
            
            camera2Manager.openCamera(selectedCameraId, stateCallback, backgroundHandler);
            
        } catch (Exception e) {
            Log.e(TAG, "Ошибка открытия Camera2: " + e.getMessage());
            if (callback != null) {
                callback.onError("Ошибка открытия камеры: " + e.getMessage());
            }
        }
    }
    
    /**
     * Legacy Camera API (для совместимости с Android < 5.0)
     */
    private void openLegacyCamera(int cameraId) {
        try {
            if (legacyCamera != null) {
                legacyCamera.release();
            }
            
            legacyCamera = Camera.open(cameraId);
            if (legacyCamera == null) {
                if (callback != null) {
                    callback.onError("Не удалось открыть камеру");
                }
                return;
            }
            
            Camera.Parameters parameters = legacyCamera.getParameters();
            List<Camera.Size> sizes = parameters.getSupportedPictureSizes();
            if (sizes.size() > 0) {
                Camera.Size largest = sizes.get(0);
                for (Camera.Size size : sizes) {
                    if (size.width * size.height > largest.width * largest.height) {
                        largest = size;
                    }
                }
                parameters.setPictureSize(largest.width, largest.height);
            }
            
            legacyCamera.setParameters(parameters);
            
            // Создаем SurfaceTexture для предпросмотра
            SurfaceTexture surfaceTexture = new SurfaceTexture(0);
            legacyCamera.setPreviewTexture(surfaceTexture);
            legacyCamera.startPreview();
            
            isCameraOpen = true;
            
            if (callback != null) {
                callback.onCameraOpened();
            }
            
        } catch (Exception e) {
            Log.e(TAG, "Ошибка открытия Legacy Camera: " + e.getMessage());
            if (callback != null) {
                callback.onError("Ошибка открытия камеры: " + e.getMessage());
            }
        }
    }
    
    /**
     * Сделать фото
     */
    public void takePhoto() {
        if (!isCameraOpen) {
            if (callback != null) {
                callback.onError("Камера не открыта");
            }
            return;
        }
        
        if (isCamera2Supported && cameraDevice != null) {
            takePhotoCamera2();
        } else if (legacyCamera != null) {
            takePhotoLegacy();
        }
    }
    
    private void takePhotoCamera2() {
        try {
            final ImageReader reader = imageReader;
            CaptureRequest.Builder captureBuilder = 
                cameraDevice.createCaptureRequest(CameraDevice.TEMPLATE_STILL_CAPTURE);
            captureBuilder.addTarget(reader.getSurface());
            
            captureBuilder.set(CaptureRequest.CONTROL_MODE, CameraMetadata.CONTROL_MODE_AUTO);
            
            CameraCaptureSession.CaptureCallback captureCallback = 
                new CameraCaptureSession.CaptureCallback() {
                    @Override
                    public void onCaptureCompleted(CameraCaptureSession session,
                                                 CaptureRequest request,
                                                 TotalCaptureResult result) {
                        Log.d(TAG, "Фото сделано (Camera2)");
                    }
                };
            
            captureSession.capture(captureBuilder.build(), captureCallback, backgroundHandler);
            
        } catch (Exception e) {
            Log.e(TAG, "Ошибка съемки Camera2: " + e.getMessage());
            if (callback != null) {
                callback.onError("Ошибка съемки: " + e.getMessage());
            }
        }
    }
    
    private void takePhotoLegacy() {
        try {
            legacyCamera.takePicture(null, null, new Camera.PictureCallback() {
                @Override
                public void onPictureTaken(byte[] data, Camera camera) {
                    try {
                        String base64 = Base64.encodeToString(data, Base64.DEFAULT);
                        if (callback != null) {
                            callback.onPhotoTaken(base64);
                        }
                        
                        // Перезапуск предпросмотра
                        legacyCamera.startPreview();
                        
                    } catch (Exception e) {
                        Log.e(TAG, "Ошибка обработки фото: " + e.getMessage());
                        if (callback != null) {
                            callback.onError("Ошибка обработки фото: " + e.getMessage());
                        }
                    }
                }
            });
            
        } catch (Exception e) {
            Log.e(TAG, "Ошибка съемки Legacy: " + e.getMessage());
            if (callback != null) {
                callback.onError("Ошибка съемки: " + e.getMessage());
            }
        }
    }
    
    /**
     * Переключение камеры (передняя/задняя)
     */
    public void switchCamera() {
        closeCamera();
        currentCameraId = (currentCameraId == 0) ? 1 : 0;
        openCamera(currentCameraId);
    }
    
    /**
     * Закрытие камеры
     */
    public void closeCamera() {
        isCameraOpen = false;
        
        if (isCamera2Supported) {
            closeCamera2();
        } else {
            closeLegacyCamera();
        }
        
        if (callback != null) {
            callback.onCameraClosed();
        }
    }
    
    private void closeCamera2() {
        try {
            if (captureSession != null) {
                captureSession.close();
                captureSession = null;
            }
            if (cameraDevice != null) {
                cameraDevice.close();
                cameraDevice = null;
            }
            if (imageReader != null) {
                imageReader.close();
                imageReader = null;
            }
            stopBackgroundThread();
        } catch (Exception e) {
            Log.e(TAG, "Ошибка закрытия Camera2: " + e.getMessage());
        }
    }
    
    private void closeLegacyCamera() {
        if (legacyCamera != null) {
            legacyCamera.stopPreview();
            legacyCamera.release();
            legacyCamera = null;
        }
    }
    
    // ==================== CAMERA2 CALLBACKS ====================
    
    private final CameraDevice.StateCallback stateCallback = new CameraDevice.StateCallback() {
        @Override
        public void onOpened(CameraDevice camera) {
            cameraDevice = camera;
            createCameraPreviewSession();
            isCameraOpen = true;
            
            if (callback != null) {
                callback.onCameraOpened();
            }
        }
        
        @Override
        public void onDisconnected(CameraDevice camera) {
            camera.close();
            cameraDevice = null;
            isCameraOpen = false;
        }
        
        @Override
        public void onError(CameraDevice camera, int error) {
            camera.close();
            cameraDevice = null;
            isCameraOpen = false;
            
            if (callback != null) {
                callback.onError("Ошибка камеры: " + error);
            }
        }
    };
    
    private void createCameraPreviewSession() {
        try {
            SurfaceTexture texture = new SurfaceTexture(0);
            texture.setDefaultBufferSize(640, 480);
            Surface surface = new Surface(texture);
            
            cameraDevice.createCaptureSession(Arrays.asList(surface, imageReader.getSurface()),
                new CameraCaptureSession.StateCallback() {
                    @Override
                    public void onConfigured(CameraCaptureSession session) {
                        if (cameraDevice == null) return;
                        captureSession = session;
                        updatePreview();
                    }
                    
                    @Override
                    public void onConfigureFailed(CameraCaptureSession session) {
                        if (callback != null) {
                            callback.onError("Ошибка конфигурации камеры");
                        }
                    }
                }, null);
        } catch (Exception e) {
            Log.e(TAG, "Ошибка создания сессии: " + e.getMessage());
        }
    }
    
    private void updatePreview() {
        if (cameraDevice == null) return;
        
        try {
            CaptureRequest.Builder builder = cameraDevice.createCaptureRequest(CameraDevice.TEMPLATE_PREVIEW);
            builder.set(CaptureRequest.CONTROL_MODE, CameraMetadata.CONTROL_MODE_AUTO);
            
            captureSession.setRepeatingRequest(builder.build(), null, backgroundHandler);
        } catch (Exception e) {
            Log.e(TAG, "Ошибка обновления предпросмотра: " + e.getMessage());
        }
    }
    
    private final ImageReader.OnImageAvailableListener onImageAvailableListener = 
        new ImageReader.OnImageAvailableListener() {
            @Override
            public void onImageAvailable(ImageReader reader) {
                try {
                    Image image = reader.acquireLatestImage();
                    if (image != null) {
                        ByteBuffer buffer = image.getPlanes()[0].getBuffer();
                        byte[] bytes = new byte[buffer.remaining()];
                        buffer.get(bytes);
                        
                        String base64 = Base64.encodeToString(bytes, Base64.DEFAULT);
                        
                        if (callback != null) {
                            callback.onPhotoTaken(base64);
                        }
                        
                        image.close();
                    }
                } catch (Exception e) {
                    Log.e(TAG, "Ошибка обработки изображения: " + e.getMessage());
                }
            }
        };
    
    // ==================== BACKGROUND THREAD ====================
    
    private void startBackgroundThread() {
        backgroundThread = new HandlerThread("CameraBackground");
        backgroundThread.start();
        backgroundHandler = new Handler(backgroundThread.getLooper());
    }
    
    private void stopBackgroundThread() {
        if (backgroundThread != null) {
            backgroundThread.quitSafely();
            try {
                backgroundThread.join();
                backgroundThread = null;
                backgroundHandler = null;
            } catch (InterruptedException e) {
                Log.e(TAG, "Ошибка остановки фонового потока: " + e.getMessage());
            }
        }
    }
    
    // ==================== HELPER CLASSES ====================
    
    static class CompareSizesByArea implements java.util.Comparator<Size> {
        @Override
        public int compare(Size lhs, Size rhs) {
            return Long.signum((long) lhs.getWidth() * lhs.getHeight() -
                             (long) rhs.getWidth() * rhs.getHeight());
        }
    }
}