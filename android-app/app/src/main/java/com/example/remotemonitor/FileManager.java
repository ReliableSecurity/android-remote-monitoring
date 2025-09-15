package com.example.remotemonitor;

import android.Manifest;
import android.content.Context;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Environment;
import android.os.StatFs;
import android.util.Base64;
import android.util.Log;
import android.webkit.MimeTypeMap;

import androidx.core.app.ActivityCompat;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.*;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.security.MessageDigest;
import java.text.SimpleDateFormat;
import java.util.*;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Файловый менеджер с возможностью передачи файлов
 * Поддержка Android 5.0-14 с адаптивными разрешениями
 */
public class FileManager {
    
    private static final String TAG = "FileManager";
    private Context context;
    private ExecutorService executorService;
    
    // Callbacks
    public interface FileCallback {
        void onFileList(JSONArray fileList);
        void onFileContent(String filename, String base64Content);
        void onFileUploaded(String filename, boolean success);
        void onFileDeleted(String filename, boolean success);
        void onProgress(String operation, int progress);
        void onError(String error);
    }
    
    private FileCallback callback;
    
    public FileManager(Context context) {
        this.context = context;
        this.executorService = Executors.newCachedThreadPool();
    }
    
    public void setCallback(FileCallback callback) {
        this.callback = callback;
    }
    
    /**
     * Проверка разрешений на файлы
     */
    public boolean hasFilePermissions() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            // Android 13+ новые разрешения
            return ActivityCompat.checkSelfPermission(context, Manifest.permission.READ_MEDIA_IMAGES) 
                    == PackageManager.PERMISSION_GRANTED ||
                   ActivityCompat.checkSelfPermission(context, Manifest.permission.READ_MEDIA_VIDEO) 
                    == PackageManager.PERMISSION_GRANTED ||
                   ActivityCompat.checkSelfPermission(context, Manifest.permission.READ_MEDIA_AUDIO) 
                    == PackageManager.PERMISSION_GRANTED;
        } else {
            // Старые разрешения
            return ActivityCompat.checkSelfPermission(context, Manifest.permission.READ_EXTERNAL_STORAGE) 
                    == PackageManager.PERMISSION_GRANTED &&
                   ActivityCompat.checkSelfPermission(context, Manifest.permission.WRITE_EXTERNAL_STORAGE) 
                    == PackageManager.PERMISSION_GRANTED;
        }
    }
    
    /**
     * Получение списка доступных путей
     */
    public JSONObject getStorageInfo() {
        JSONObject info = new JSONObject();
        
        try {
            JSONArray paths = new JSONArray();
            
            // Внутреннее хранилище
            File internal = Environment.getDataDirectory();
            if (internal.exists()) {
                JSONObject internalInfo = new JSONObject();
                internalInfo.put("path", internal.getAbsolutePath());
                internalInfo.put("type", "internal");
                internalInfo.put("readable", internal.canRead());
                internalInfo.put("writable", internal.canWrite());
                
                StatFs stat = new StatFs(internal.getPath());
                long totalBytes = stat.getBlockCountLong() * stat.getBlockSizeLong();
                long freeBytes = stat.getAvailableBlocksLong() * stat.getBlockSizeLong();
                
                internalInfo.put("total_space", totalBytes);
                internalInfo.put("free_space", freeBytes);
                internalInfo.put("used_space", totalBytes - freeBytes);
                
                paths.put(internalInfo);
            }
            
            // Внешнее хранилище
            if (Environment.getExternalStorageState().equals(Environment.MEDIA_MOUNTED)) {
                File external = Environment.getExternalStorageDirectory();
                if (external.exists()) {
                    JSONObject externalInfo = new JSONObject();
                    externalInfo.put("path", external.getAbsolutePath());
                    externalInfo.put("type", "external");
                    externalInfo.put("readable", external.canRead());
                    externalInfo.put("writable", external.canWrite());
                    
                    StatFs stat = new StatFs(external.getPath());
                    long totalBytes = stat.getBlockCountLong() * stat.getBlockSizeLong();
                    long freeBytes = stat.getAvailableBlocksLong() * stat.getBlockSizeLong();
                    
                    externalInfo.put("total_space", totalBytes);
                    externalInfo.put("free_space", freeBytes);
                    externalInfo.put("used_space", totalBytes - freeBytes);
                    
                    paths.put(externalInfo);
                }
            }
            
            // SD карта и другие внешние устройства
            File[] externalDirs = context.getExternalFilesDirs(null);
            for (int i = 1; i < externalDirs.length; i++) {
                if (externalDirs[i] != null && externalDirs[i].exists()) {
                    JSONObject sdInfo = new JSONObject();
                    sdInfo.put("path", externalDirs[i].getAbsolutePath());
                    sdInfo.put("type", "removable");
                    sdInfo.put("readable", externalDirs[i].canRead());
                    sdInfo.put("writable", externalDirs[i].canWrite());
                    paths.put(sdInfo);
                }
            }
            
            info.put("storage_paths", paths);
            info.put("has_permissions", hasFilePermissions());
            info.put("sdk_version", Build.VERSION.SDK_INT);
            
        } catch (JSONException e) {
            Log.e(TAG, "Ошибка получения информации о хранилище: " + e.getMessage());
        }
        
        return info;
    }
    
    /**
     * Получение списка файлов в директории
     */
    public void listFiles(String directoryPath) {
        if (!hasFilePermissions()) {
            if (callback != null) {
                callback.onError("Нет разрешения на доступ к файлам");
            }
            return;
        }
        
        executorService.execute(() -> {
            try {
                File directory = new File(directoryPath);
                
                if (!directory.exists()) {
                    if (callback != null) {
                        callback.onError("Директория не существует: " + directoryPath);
                    }
                    return;
                }
                
                if (!directory.isDirectory()) {
                    if (callback != null) {
                        callback.onError("Путь не является директорией: " + directoryPath);
                    }
                    return;
                }
                
                File[] files = directory.listFiles();
                if (files == null) {
                    if (callback != null) {
                        callback.onError("Не удалось получить список файлов");
                    }
                    return;
                }
                
                JSONArray fileList = new JSONArray();
                SimpleDateFormat dateFormat = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US);
                
                // Добавление родительской директории
                File parent = directory.getParentFile();
                if (parent != null) {
                    JSONObject parentInfo = new JSONObject();
                    parentInfo.put("name", "..");
                    parentInfo.put("path", parent.getAbsolutePath());
                    parentInfo.put("type", "directory");
                    parentInfo.put("size", 0);
                    parentInfo.put("is_parent", true);
                    fileList.put(parentInfo);
                }
                
                // Сортировка: сначала папки, потом файлы
                Arrays.sort(files, (f1, f2) -> {
                    if (f1.isDirectory() && !f2.isDirectory()) return -1;
                    if (!f1.isDirectory() && f2.isDirectory()) return 1;
                    return f1.getName().compareToIgnoreCase(f2.getName());
                });
                
                for (File file : files) {
                    try {
                        JSONObject fileInfo = new JSONObject();
                        fileInfo.put("name", file.getName());
                        fileInfo.put("path", file.getAbsolutePath());
                        fileInfo.put("type", file.isDirectory() ? "directory" : "file");
                        fileInfo.put("size", file.isDirectory() ? 0 : file.length());
                        fileInfo.put("last_modified", dateFormat.format(new Date(file.lastModified())));
                        fileInfo.put("readable", file.canRead());
                        fileInfo.put("writable", file.canWrite());
                        fileInfo.put("executable", file.canExecute());
                        fileInfo.put("hidden", file.isHidden());
                        
                        if (!file.isDirectory()) {
                            String mimeType = getMimeType(file.getName());
                            fileInfo.put("mime_type", mimeType);
                            fileInfo.put("extension", getFileExtension(file.getName()));
                        }
                        
                        fileList.put(fileInfo);
                        
                    } catch (JSONException e) {
                        Log.w(TAG, "Ошибка обработки файла " + file.getName() + ": " + e.getMessage());
                    }
                }
                
                if (callback != null) {
                    callback.onFileList(fileList);
                }
                
            } catch (Exception e) {
                Log.e(TAG, "Ошибка получения списка файлов: " + e.getMessage());
                if (callback != null) {
                    callback.onError("Ошибка: " + e.getMessage());
                }
            }
        });
    }
    
    /**
     * Чтение содержимого файла
     */
    public void readFile(String filePath) {
        if (!hasFilePermissions()) {
            if (callback != null) {
                callback.onError("Нет разрешения на чтение файлов");
            }
            return;
        }
        
        executorService.execute(() -> {
            try {
                File file = new File(filePath);
                
                if (!file.exists()) {
                    if (callback != null) {
                        callback.onError("Файл не существует: " + filePath);
                    }
                    return;
                }
                
                if (file.isDirectory()) {
                    if (callback != null) {
                        callback.onError("Путь является директорией: " + filePath);
                    }
                    return;
                }
                
                // Проверка размера файла (ограничение 50MB)
                if (file.length() > 50 * 1024 * 1024) {
                    if (callback != null) {
                        callback.onError("Файл слишком большой (больше 50MB)");
                    }
                    return;
                }
                
                // Чтение файла
                byte[] fileContent = new byte[(int) file.length()];
                FileInputStream fis = new FileInputStream(file);
                int bytesRead = fis.read(fileContent);
                fis.close();
                
                if (bytesRead != file.length()) {
                    if (callback != null) {
                        callback.onError("Не удалось прочитать весь файл");
                    }
                    return;
                }
                
                // Кодирование в Base64
                String base64Content = Base64.encodeToString(fileContent, Base64.DEFAULT);
                
                if (callback != null) {
                    callback.onFileContent(file.getName(), base64Content);
                }
                
            } catch (Exception e) {
                Log.e(TAG, "Ошибка чтения файла: " + e.getMessage());
                if (callback != null) {
                    callback.onError("Ошибка чтения: " + e.getMessage());
                }
            }
        });
    }
    
    /**
     * Запись файла из Base64
     */
    public void writeFile(String filePath, String base64Content) {
        if (!hasFilePermissions()) {
            if (callback != null) {
                callback.onError("Нет разрешения на запись файлов");
            }
            return;
        }
        
        executorService.execute(() -> {
            try {
                File file = new File(filePath);
                File parentDir = file.getParentFile();
                
                if (parentDir != null && !parentDir.exists()) {
                    if (!parentDir.mkdirs()) {
                        if (callback != null) {
                            callback.onError("Не удалось создать директорию: " + parentDir.getPath());
                        }
                        return;
                    }
                }
                
                // Декодирование Base64
                byte[] fileContent = Base64.decode(base64Content, Base64.DEFAULT);
                
                // Запись файла
                FileOutputStream fos = new FileOutputStream(file);
                fos.write(fileContent);
                fos.close();
                
                if (callback != null) {
                    callback.onFileUploaded(file.getName(), true);
                }
                
                Log.i(TAG, "Файл записан: " + filePath + " (" + fileContent.length + " байт)");
                
            } catch (Exception e) {
                Log.e(TAG, "Ошибка записи файла: " + e.getMessage());
                if (callback != null) {
                    callback.onFileUploaded(filePath, false);
                    callback.onError("Ошибка записи: " + e.getMessage());
                }
            }
        });
    }
    
    /**
     * Удаление файла или директории
     */
    public void deleteFile(String filePath) {
        if (!hasFilePermissions()) {
            if (callback != null) {
                callback.onError("Нет разрешения на удаление файлов");
            }
            return;
        }
        
        executorService.execute(() -> {
            try {
                File file = new File(filePath);
                
                if (!file.exists()) {
                    if (callback != null) {
                        callback.onError("Файл не существует: " + filePath);
                    }
                    return;
                }
                
                boolean deleted;
                if (file.isDirectory()) {
                    deleted = deleteDirectory(file);
                } else {
                    deleted = file.delete();
                }
                
                if (callback != null) {
                    callback.onFileDeleted(file.getName(), deleted);
                }
                
                if (deleted) {
                    Log.i(TAG, "Удален: " + filePath);
                } else {
                    Log.w(TAG, "Не удалось удалить: " + filePath);
                }
                
            } catch (Exception e) {
                Log.e(TAG, "Ошибка удаления: " + e.getMessage());
                if (callback != null) {
                    callback.onFileDeleted(filePath, false);
                    callback.onError("Ошибка удаления: " + e.getMessage());
                }
            }
        });
    }
    
    /**
     * Создание директории
     */
    public void createDirectory(String dirPath) {
        if (!hasFilePermissions()) {
            if (callback != null) {
                callback.onError("Нет разрешения на создание директорий");
            }
            return;
        }
        
        executorService.execute(() -> {
            try {
                File dir = new File(dirPath);
                
                if (dir.exists()) {
                    if (callback != null) {
                        callback.onError("Директория уже существует: " + dirPath);
                    }
                    return;
                }
                
                boolean created = dir.mkdirs();
                
                if (callback != null) {
                    if (created) {
                        callback.onFileUploaded(dir.getName(), true);
                    } else {
                        callback.onError("Не удалось создать директорию: " + dirPath);
                    }
                }
                
            } catch (Exception e) {
                Log.e(TAG, "Ошибка создания директории: " + e.getMessage());
                if (callback != null) {
                    callback.onError("Ошибка создания директории: " + e.getMessage());
                }
            }
        });
    }
    
    /**
     * Поиск файлов
     */
    public void searchFiles(String rootPath, String searchQuery, String fileType) {
        if (!hasFilePermissions()) {
            if (callback != null) {
                callback.onError("Нет разрешения на поиск файлов");
            }
            return;
        }
        
        executorService.execute(() -> {
            try {
                File rootDir = new File(rootPath);
                if (!rootDir.exists() || !rootDir.isDirectory()) {
                    if (callback != null) {
                        callback.onError("Корневая директория не найдена: " + rootPath);
                    }
                    return;
                }
                
                JSONArray results = new JSONArray();
                searchFilesRecursive(rootDir, searchQuery.toLowerCase(), fileType, results, 0);
                
                if (callback != null) {
                    callback.onFileList(results);
                }
                
            } catch (Exception e) {
                Log.e(TAG, "Ошибка поиска: " + e.getMessage());
                if (callback != null) {
                    callback.onError("Ошибка поиска: " + e.getMessage());
                }
            }
        });
    }
    
    // ==================== HELPER METHODS ====================
    
    private boolean deleteDirectory(File dir) {
        File[] files = dir.listFiles();
        if (files != null) {
            for (File file : files) {
                if (file.isDirectory()) {
                    deleteDirectory(file);
                } else {
                    file.delete();
                }
            }
        }
        return dir.delete();
    }
    
    private void searchFilesRecursive(File dir, String query, String fileType, 
                                    JSONArray results, int depth) throws JSONException {
        if (depth > 10) return; // Ограничение глубины
        
        File[] files = dir.listFiles();
        if (files == null) return;
        
        for (File file : files) {
            String fileName = file.getName().toLowerCase();
            
            // Проверка соответствия запросу
            boolean matchesQuery = query.isEmpty() || fileName.contains(query);
            
            // Проверка типа файла
            boolean matchesType = fileType.equals("all") || 
                                (fileType.equals("image") && isImageFile(fileName)) ||
                                (fileType.equals("video") && isVideoFile(fileName)) ||
                                (fileType.equals("audio") && isAudioFile(fileName)) ||
                                (fileType.equals("document") && isDocumentFile(fileName));
            
            if (file.isFile() && matchesQuery && matchesType) {
                JSONObject fileInfo = new JSONObject();
                fileInfo.put("name", file.getName());
                fileInfo.put("path", file.getAbsolutePath());
                fileInfo.put("size", file.length());
                fileInfo.put("type", "file");
                fileInfo.put("mime_type", getMimeType(file.getName()));
                results.put(fileInfo);
            } else if (file.isDirectory()) {
                searchFilesRecursive(file, query, fileType, results, depth + 1);
            }
        }
    }
    
    private String getMimeType(String fileName) {
        String extension = getFileExtension(fileName);
        return MimeTypeMap.getSingleton().getMimeTypeFromExtension(extension);
    }
    
    private String getFileExtension(String fileName) {
        int lastDot = fileName.lastIndexOf('.');
        return lastDot > 0 ? fileName.substring(lastDot + 1).toLowerCase() : "";
    }
    
    private boolean isImageFile(String fileName) {
        String ext = getFileExtension(fileName);
        return Arrays.asList("jpg", "jpeg", "png", "gif", "bmp", "webp", "svg").contains(ext);
    }
    
    private boolean isVideoFile(String fileName) {
        String ext = getFileExtension(fileName);
        return Arrays.asList("mp4", "avi", "mkv", "mov", "wmv", "flv", "webm", "m4v").contains(ext);
    }
    
    private boolean isAudioFile(String fileName) {
        String ext = getFileExtension(fileName);
        return Arrays.asList("mp3", "wav", "flac", "aac", "ogg", "m4a", "wma").contains(ext);
    }
    
    private boolean isDocumentFile(String fileName) {
        String ext = getFileExtension(fileName);
        return Arrays.asList("pdf", "doc", "docx", "txt", "rtf", "odt", "xls", "xlsx").contains(ext);
    }
    
    /**
     * Получение хеша файла
     */
    public void getFileHash(String filePath) {
        executorService.execute(() -> {
            try {
                File file = new File(filePath);
                if (!file.exists() || file.isDirectory()) {
                    if (callback != null) {
                        callback.onError("Файл не найден или является директорией");
                    }
                    return;
                }
                
                MessageDigest md = MessageDigest.getInstance("SHA-256");
                FileInputStream fis = new FileInputStream(file);
                
                byte[] buffer = new byte[8192];
                int bytesRead;
                
                while ((bytesRead = fis.read(buffer)) != -1) {
                    md.update(buffer, 0, bytesRead);
                }
                
                fis.close();
                
                byte[] hash = md.digest();
                StringBuilder hashString = new StringBuilder();
                for (byte b : hash) {
                    hashString.append(String.format("%02x", b));
                }
                
                Log.i(TAG, "SHA-256 хеш файла " + file.getName() + ": " + hashString.toString());
                
            } catch (Exception e) {
                Log.e(TAG, "Ошибка вычисления хеша: " + e.getMessage());
            }
        });
    }
    
    /**
     * Очистка ресурсов
     */
    public void cleanup() {
        if (executorService != null && !executorService.isShutdown()) {
            executorService.shutdown();
        }
        Log.i(TAG, "FileManager очищен");
    }
}