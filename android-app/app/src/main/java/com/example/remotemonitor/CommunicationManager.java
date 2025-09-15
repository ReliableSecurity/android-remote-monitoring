package com.example.remotemonitor;

import android.Manifest;
import android.content.ContentResolver;
import android.content.Context;
import android.content.pm.PackageManager;
import android.database.Cursor;
import android.net.Uri;
import android.os.Build;
import android.provider.CallLog;
import android.provider.ContactsContract;
import android.provider.Telephony;
import android.telephony.SmsManager;
import android.telephony.TelephonyManager;
import android.util.Log;

import androidx.core.app.ActivityCompat;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Менеджер для работы с SMS и звонками
 * Поддержка Android 5.0-14 с согласием пользователя
 */
public class CommunicationManager {
    
    private static final String TAG = "CommunicationManager";
    private Context context;
    private ExecutorService executorService;
    
    // Callbacks
    public interface CommunicationCallback {
        void onSmsListReceived(JSONArray smsList);
        void onCallLogReceived(JSONArray callLog);
        void onContactsReceived(JSONArray contacts);
        void onSmsSent(boolean success, String message);
        void onDeviceInfoReceived(JSONObject deviceInfo);
        void onError(String error);
    }
    
    private CommunicationCallback callback;
    
    public CommunicationManager(Context context) {
        this.context = context;
        this.executorService = Executors.newCachedThreadPool();
    }
    
    public void setCallback(CommunicationCallback callback) {
        this.callback = callback;
    }
    
    /**
     * Проверка разрешений на SMS
     */
    public boolean hasSmsPermissions() {
        return ActivityCompat.checkSelfPermission(context, Manifest.permission.READ_SMS) 
                == PackageManager.PERMISSION_GRANTED &&
               ActivityCompat.checkSelfPermission(context, Manifest.permission.SEND_SMS) 
                == PackageManager.PERMISSION_GRANTED;
    }
    
    /**
     * Проверка разрешений на звонки
     */
    public boolean hasCallPermissions() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            return ActivityCompat.checkSelfPermission(context, Manifest.permission.READ_CALL_LOG) 
                    == PackageManager.PERMISSION_GRANTED &&
                   ActivityCompat.checkSelfPermission(context, Manifest.permission.READ_PHONE_STATE) 
                    == PackageManager.PERMISSION_GRANTED;
        } else {
            return ActivityCompat.checkSelfPermission(context, Manifest.permission.READ_PHONE_STATE) 
                    == PackageManager.PERMISSION_GRANTED;
        }
    }
    
    /**
     * Проверка разрешений на контакты
     */
    public boolean hasContactsPermissions() {
        return ActivityCompat.checkSelfPermission(context, Manifest.permission.READ_CONTACTS) 
                == PackageManager.PERMISSION_GRANTED;
    }
    
    /**
     * Получение информации о SIM-карте и устройстве
     */
    public void getDeviceInfo() {
        executorService.execute(() -> {
            try {
                TelephonyManager telephonyManager = (TelephonyManager) context.getSystemService(Context.TELEPHONY_SERVICE);
                JSONObject info = new JSONObject();
                
                // Основная информация
                info.put("has_sms_permissions", hasSmsPermissions());
                info.put("has_call_permissions", hasCallPermissions());
                info.put("has_contacts_permissions", hasContactsPermissions());
                
                if (hasCallPermissions()) {
                    // Информация об операторе
                    String networkOperator = telephonyManager.getNetworkOperatorName();
                    String simOperator = telephonyManager.getSimOperatorName();
                    
                    info.put("network_operator", networkOperator != null ? networkOperator : "Unknown");
                    info.put("sim_operator", simOperator != null ? simOperator : "Unknown");
                    
                    // Тип сети
                    int networkType = telephonyManager.getNetworkType();
                    info.put("network_type", getNetworkTypeString(networkType));
                    
                    // Состояние SIM
                    int simState = telephonyManager.getSimState();
                    info.put("sim_state", getSimStateString(simState));
                    
                    // Для новых версий Android
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                        int phoneCount = telephonyManager.getPhoneCount();
                        info.put("phone_count", phoneCount);
                    }
                    
                    // IMEI (только если есть разрешение)
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                        if (ActivityCompat.checkSelfPermission(context, Manifest.permission.READ_PHONE_STATE) 
                            == PackageManager.PERMISSION_GRANTED) {
                            try {
                                String imei = telephonyManager.getImei();
                                info.put("imei", imei != null ? imei.substring(0, 4) + "***" : "Unknown");
                            } catch (SecurityException e) {
                                info.put("imei", "Permission denied");
                            }
                        }
                    }
                }
                
                info.put("sdk_version", Build.VERSION.SDK_INT);
                info.put("timestamp", System.currentTimeMillis());
                
                if (callback != null) {
                    callback.onDeviceInfoReceived(info);
                }
                
            } catch (Exception e) {
                Log.e(TAG, "Ошибка получения информации об устройстве: " + e.getMessage());
                if (callback != null) {
                    callback.onError("Ошибка: " + e.getMessage());
                }
            }
        });
    }
    
    /**
     * Получение SMS сообщений
     */
    public void getSmsMessages(int limit, long sinceTimestamp) {
        if (!hasSmsPermissions()) {
            if (callback != null) {
                callback.onError("Нет разрешения на чтение SMS");
            }
            return;
        }
        
        executorService.execute(() -> {
            try {
                ContentResolver contentResolver = context.getContentResolver();
                Uri smsUri = Telephony.Sms.CONTENT_URI;
                
                String selection = null;
                String[] selectionArgs = null;
                
                if (sinceTimestamp > 0) {
                    selection = Telephony.Sms.DATE + " > ?";
                    selectionArgs = new String[]{String.valueOf(sinceTimestamp)};
                }
                
                String sortOrder = Telephony.Sms.DATE + " DESC LIMIT " + limit;
                
                Cursor cursor = contentResolver.query(
                    smsUri,
                    null,
                    selection,
                    selectionArgs,
                    sortOrder
                );
                
                if (cursor == null) {
                    if (callback != null) {
                        callback.onError("Не удалось получить доступ к SMS");
                    }
                    return;
                }
                
                JSONArray smsArray = new JSONArray();
                SimpleDateFormat dateFormat = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US);
                
                while (cursor.moveToNext()) {
                    try {
                        JSONObject sms = new JSONObject();
                        
                        // Основные поля
                        String address = cursor.getString(cursor.getColumnIndexOrThrow(Telephony.Sms.ADDRESS));
                        String body = cursor.getString(cursor.getColumnIndexOrThrow(Telephony.Sms.BODY));
                        long date = cursor.getLong(cursor.getColumnIndexOrThrow(Telephony.Sms.DATE));
                        int type = cursor.getInt(cursor.getColumnIndexOrThrow(Telephony.Sms.TYPE));
                        int read = cursor.getInt(cursor.getColumnIndexOrThrow(Telephony.Sms.READ));
                        
                        sms.put("address", address != null ? address : "Unknown");
                        sms.put("body", body != null ? body : "");
                        sms.put("date", dateFormat.format(new Date(date)));
                        sms.put("timestamp", date);
                        sms.put("type", getSmsTypeString(type));
                        sms.put("read", read == 1);
                        
                        // Дополнительные поля
                        try {
                            int threadId = cursor.getInt(cursor.getColumnIndexOrThrow(Telephony.Sms.THREAD_ID));
                            sms.put("thread_id", threadId);
                        } catch (Exception e) {
                            sms.put("thread_id", -1);
                        }
                        
                        // Контакт (если доступен)
                        if (hasContactsPermissions() && address != null) {
                            String contactName = getContactName(address);
                            sms.put("contact_name", contactName);
                        }
                        
                        smsArray.put(sms);
                        
                    } catch (JSONException e) {
                        Log.w(TAG, "Ошибка обработки SMS: " + e.getMessage());
                    }
                }
                
                cursor.close();
                
                if (callback != null) {
                    callback.onSmsListReceived(smsArray);
                }
                
                Log.i(TAG, "Получено SMS сообщений: " + smsArray.length());
                
            } catch (Exception e) {
                Log.e(TAG, "Ошибка получения SMS: " + e.getMessage());
                if (callback != null) {
                    callback.onError("Ошибка получения SMS: " + e.getMessage());
                }
            }
        });
    }
    
    /**
     * Получение истории звонков
     */
    public void getCallLog(int limit, long sinceTimestamp) {
        if (!hasCallPermissions()) {
            if (callback != null) {
                callback.onError("Нет разрешения на чтение истории звонков");
            }
            return;
        }
        
        executorService.execute(() -> {
            try {
                ContentResolver contentResolver = context.getContentResolver();
                Uri callLogUri = CallLog.Calls.CONTENT_URI;
                
                String selection = null;
                String[] selectionArgs = null;
                
                if (sinceTimestamp > 0) {
                    selection = CallLog.Calls.DATE + " > ?";
                    selectionArgs = new String[]{String.valueOf(sinceTimestamp)};
                }
                
                String sortOrder = CallLog.Calls.DATE + " DESC LIMIT " + limit;
                
                Cursor cursor = contentResolver.query(
                    callLogUri,
                    null,
                    selection,
                    selectionArgs,
                    sortOrder
                );
                
                if (cursor == null) {
                    if (callback != null) {
                        callback.onError("Не удалось получить доступ к истории звонков");
                    }
                    return;
                }
                
                JSONArray callArray = new JSONArray();
                SimpleDateFormat dateFormat = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US);
                
                while (cursor.moveToNext()) {
                    try {
                        JSONObject call = new JSONObject();
                        
                        String number = cursor.getString(cursor.getColumnIndexOrThrow(CallLog.Calls.NUMBER));
                        String name = cursor.getString(cursor.getColumnIndexOrThrow(CallLog.Calls.CACHED_NAME));
                        long date = cursor.getLong(cursor.getColumnIndexOrThrow(CallLog.Calls.DATE));
                        long duration = cursor.getLong(cursor.getColumnIndexOrThrow(CallLog.Calls.DURATION));
                        int type = cursor.getInt(cursor.getColumnIndexOrThrow(CallLog.Calls.TYPE));
                        
                        call.put("number", number != null ? number : "Unknown");
                        call.put("name", name != null ? name : "");
                        call.put("date", dateFormat.format(new Date(date)));
                        call.put("timestamp", date);
                        call.put("duration", duration);
                        call.put("duration_formatted", formatDuration(duration));
                        call.put("type", getCallTypeString(type));
                        
                        // Дополнительные поля
                        try {
                            String location = cursor.getString(cursor.getColumnIndexOrThrow(CallLog.Calls.GEOCODED_LOCATION));
                            call.put("location", location != null ? location : "");
                        } catch (Exception e) {
                            call.put("location", "");
                        }
                        
                        // Контакт (если не найден в кеше и есть разрешения)
                        if ((name == null || name.isEmpty()) && hasContactsPermissions() && number != null) {
                            String contactName = getContactName(number);
                            call.put("contact_name", contactName);
                        }
                        
                        callArray.put(call);
                        
                    } catch (JSONException e) {
                        Log.w(TAG, "Ошибка обработки записи звонка: " + e.getMessage());
                    }
                }
                
                cursor.close();
                
                if (callback != null) {
                    callback.onCallLogReceived(callArray);
                }
                
                Log.i(TAG, "Получено записей звонков: " + callArray.length());
                
            } catch (Exception e) {
                Log.e(TAG, "Ошибка получения истории звонков: " + e.getMessage());
                if (callback != null) {
                    callback.onError("Ошибка получения истории звонков: " + e.getMessage());
                }
            }
        });
    }
    
    /**
     * Получение контактов
     */
    public void getContacts(int limit) {
        if (!hasContactsPermissions()) {
            if (callback != null) {
                callback.onError("Нет разрешения на чтение контактов");
            }
            return;
        }
        
        executorService.execute(() -> {
            try {
                ContentResolver contentResolver = context.getContentResolver();
                Uri contactsUri = ContactsContract.CommonDataKinds.Phone.CONTENT_URI;
                
                String[] projection = {
                    ContactsContract.CommonDataKinds.Phone.CONTACT_ID,
                    ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME,
                    ContactsContract.CommonDataKinds.Phone.NUMBER,
                    ContactsContract.CommonDataKinds.Phone.TYPE,
                    ContactsContract.CommonDataKinds.Phone.LABEL
                };
                
                String sortOrder = ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME + 
                                 " ASC LIMIT " + limit;
                
                Cursor cursor = contentResolver.query(
                    contactsUri,
                    projection,
                    null,
                    null,
                    sortOrder
                );
                
                if (cursor == null) {
                    if (callback != null) {
                        callback.onError("Не удалось получить доступ к контактам");
                    }
                    return;
                }
                
                JSONArray contactsArray = new JSONArray();
                
                while (cursor.moveToNext()) {
                    try {
                        JSONObject contact = new JSONObject();
                        
                        String contactId = cursor.getString(0);
                        String name = cursor.getString(1);
                        String phoneNumber = cursor.getString(2);
                        int phoneType = cursor.getInt(3);
                        String phoneLabel = cursor.getString(4);
                        
                        contact.put("contact_id", contactId != null ? contactId : "");
                        contact.put("name", name != null ? name : "");
                        contact.put("phone_number", phoneNumber != null ? phoneNumber : "");
                        contact.put("phone_type", getPhoneTypeString(phoneType, phoneLabel));
                        
                        contactsArray.put(contact);
                        
                    } catch (JSONException e) {
                        Log.w(TAG, "Ошибка обработки контакта: " + e.getMessage());
                    }
                }
                
                cursor.close();
                
                if (callback != null) {
                    callback.onContactsReceived(contactsArray);
                }
                
                Log.i(TAG, "Получено контактов: " + contactsArray.length());
                
            } catch (Exception e) {
                Log.e(TAG, "Ошибка получения контактов: " + e.getMessage());
                if (callback != null) {
                    callback.onError("Ошибка получения контактов: " + e.getMessage());
                }
            }
        });
    }
    
    /**
     * Отправка SMS
     */
    public void sendSms(String phoneNumber, String message) {
        if (!hasSmsPermissions()) {
            if (callback != null) {
                callback.onError("Нет разрешения на отправку SMS");
            }
            return;
        }
        
        executorService.execute(() -> {
            try {
                SmsManager smsManager = SmsManager.getDefault();
                
                // Разделение длинных сообщений
                ArrayList<String> parts = smsManager.divideMessage(message);
                
                if (parts.size() == 1) {
                    smsManager.sendTextMessage(phoneNumber, null, message, null, null);
                } else {
                    smsManager.sendMultipartTextMessage(phoneNumber, null, parts, null, null);
                }
                
                if (callback != null) {
                    callback.onSmsSent(true, "SMS отправлено успешно");
                }
                
                Log.i(TAG, "SMS отправлено на номер: " + phoneNumber);
                
            } catch (Exception e) {
                Log.e(TAG, "Ошибка отправки SMS: " + e.getMessage());
                if (callback != null) {
                    callback.onSmsSent(false, "Ошибка отправки: " + e.getMessage());
                }
            }
        });
    }
    
    // ==================== HELPER METHODS ====================
    
    private String getContactName(String phoneNumber) {
        try {
            Uri uri = Uri.withAppendedPath(ContactsContract.PhoneLookup.CONTENT_FILTER_URI, 
                                         Uri.encode(phoneNumber));
            String[] projection = {ContactsContract.PhoneLookup.DISPLAY_NAME};
            
            Cursor cursor = context.getContentResolver().query(uri, projection, null, null, null);
            
            if (cursor != null && cursor.moveToFirst()) {
                String name = cursor.getString(0);
                cursor.close();
                return name != null ? name : "";
            }
            
            if (cursor != null) {
                cursor.close();
            }
            
        } catch (Exception e) {
            Log.w(TAG, "Ошибка поиска контакта: " + e.getMessage());
        }
        
        return "";
    }
    
    private String getSmsTypeString(int type) {
        switch (type) {
            case Telephony.Sms.MESSAGE_TYPE_INBOX: return "received";
            case Telephony.Sms.MESSAGE_TYPE_SENT: return "sent";
            case Telephony.Sms.MESSAGE_TYPE_DRAFT: return "draft";
            case Telephony.Sms.MESSAGE_TYPE_OUTBOX: return "outbox";
            case Telephony.Sms.MESSAGE_TYPE_FAILED: return "failed";
            case Telephony.Sms.MESSAGE_TYPE_QUEUED: return "queued";
            default: return "unknown";
        }
    }
    
    private String getCallTypeString(int type) {
        switch (type) {
            case CallLog.Calls.INCOMING_TYPE: return "incoming";
            case CallLog.Calls.OUTGOING_TYPE: return "outgoing";
            case CallLog.Calls.MISSED_TYPE: return "missed";
            case CallLog.Calls.VOICEMAIL_TYPE: return "voicemail";
            case CallLog.Calls.REJECTED_TYPE: return "rejected";
            case CallLog.Calls.BLOCKED_TYPE: return "blocked";
            default: return "unknown";
        }
    }
    
    private String getPhoneTypeString(int type, String label) {
        switch (type) {
            case ContactsContract.CommonDataKinds.Phone.TYPE_MOBILE: return "mobile";
            case ContactsContract.CommonDataKinds.Phone.TYPE_HOME: return "home";
            case ContactsContract.CommonDataKinds.Phone.TYPE_WORK: return "work";
            case ContactsContract.CommonDataKinds.Phone.TYPE_FAX_WORK: return "work_fax";
            case ContactsContract.CommonDataKinds.Phone.TYPE_FAX_HOME: return "home_fax";
            case ContactsContract.CommonDataKinds.Phone.TYPE_PAGER: return "pager";
            case ContactsContract.CommonDataKinds.Phone.TYPE_CUSTOM:
                return label != null ? label : "custom";
            default: return "other";
        }
    }
    
    private String getNetworkTypeString(int type) {
        switch (type) {
            case TelephonyManager.NETWORK_TYPE_GPRS: return "GPRS";
            case TelephonyManager.NETWORK_TYPE_EDGE: return "EDGE";
            case TelephonyManager.NETWORK_TYPE_UMTS: return "UMTS";
            case TelephonyManager.NETWORK_TYPE_HSDPA: return "HSDPA";
            case TelephonyManager.NETWORK_TYPE_HSUPA: return "HSUPA";
            case TelephonyManager.NETWORK_TYPE_HSPA: return "HSPA";
            case TelephonyManager.NETWORK_TYPE_CDMA: return "CDMA";
            case TelephonyManager.NETWORK_TYPE_EVDO_0: return "EVDO_0";
            case TelephonyManager.NETWORK_TYPE_EVDO_A: return "EVDO_A";
            case TelephonyManager.NETWORK_TYPE_EVDO_B: return "EVDO_B";
            case TelephonyManager.NETWORK_TYPE_1xRTT: return "1xRTT";
            case TelephonyManager.NETWORK_TYPE_IDEN: return "iDEN";
            case TelephonyManager.NETWORK_TYPE_LTE: return "LTE";
            case TelephonyManager.NETWORK_TYPE_EHRPD: return "eHRPD";
            case TelephonyManager.NETWORK_TYPE_HSPAP: return "HSPA+";
            default: return "Unknown";
        }
    }
    
    private String getSimStateString(int state) {
        switch (state) {
            case TelephonyManager.SIM_STATE_ABSENT: return "absent";
            case TelephonyManager.SIM_STATE_PIN_REQUIRED: return "pin_required";
            case TelephonyManager.SIM_STATE_PUK_REQUIRED: return "puk_required";
            case TelephonyManager.SIM_STATE_NETWORK_LOCKED: return "network_locked";
            case TelephonyManager.SIM_STATE_READY: return "ready";
            case TelephonyManager.SIM_STATE_NOT_READY: return "not_ready";
            case TelephonyManager.SIM_STATE_PERM_DISABLED: return "perm_disabled";
            case TelephonyManager.SIM_STATE_CARD_IO_ERROR: return "card_io_error";
            case TelephonyManager.SIM_STATE_CARD_RESTRICTED: return "card_restricted";
            default: return "unknown";
        }
    }
    
    private String formatDuration(long durationSeconds) {
        if (durationSeconds == 0) {
            return "0:00";
        }
        
        long hours = durationSeconds / 3600;
        long minutes = (durationSeconds % 3600) / 60;
        long seconds = durationSeconds % 60;
        
        if (hours > 0) {
            return String.format(Locale.US, "%d:%02d:%02d", hours, minutes, seconds);
        } else {
            return String.format(Locale.US, "%d:%02d", minutes, seconds);
        }
    }
    
    /**
     * Очистка ресурсов
     */
    public void cleanup() {
        if (executorService != null && !executorService.isShutdown()) {
            executorService.shutdown();
        }
        Log.i(TAG, "CommunicationManager очищен");
    }
}