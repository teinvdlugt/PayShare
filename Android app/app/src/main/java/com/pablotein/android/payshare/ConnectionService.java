package com.pablotein.android.payshare;

import android.app.Service;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.os.Binder;
import android.os.Bundle;
import android.os.Handler;
import android.os.IBinder;
import android.preference.PreferenceManager;
import android.support.annotation.Nullable;
import android.util.Log;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.nio.ByteBuffer;
import java.util.ArrayList;

public class ConnectionService extends Service {
    IBinder binder = new ConnectionServiceBinder();
    Socket socket;
    ArrayList<OnConnectionChangeListener> connectionChangeListeners = new ArrayList<>();
    ArrayList<OnImageDownloadedListener> imageDownloadedListeners = new ArrayList<>();
    OnLoginListener loginListener;

    private static String TAG = "ConnectionService", SERVER_IP = "192.168.1.50";
    private static int SERVER_PORT = 1234, SERVER_TIMEOUT = 2000;

    public static final int REQUEST_INFO = 0, REQUEST_LOGIN_GOOGLE = 1, REQUEST_IMAGE = 5;
    public static final String EXTRA_LOGIN_GOOGLE_TOKEN = "ExtraLoginGoogleToken", EXTRA_USER_ID = "ExtraUserID";

    public interface OnConnectionChangeListener {
        void onConnected();

        void onDisconnected();

        void onConnecting();
    }

    public interface OnLoginListener {
        void onLogin();
    }

    public interface OnImageDownloadedListener {
        void onImageDownloaded(String id, Bitmap image);
    }

    public class ConnectionServiceBinder extends Binder {
        void sendRequest(int request, Bundle extras) {
            ConnectionService.this.sendRequest(request, extras);
        }

        void addOnConnectionChangeListener(OnConnectionChangeListener listener) {
            connectionChangeListeners.add(listener);
        }

        void removeOnConnectionChangeListener(OnConnectionChangeListener listener) {
            connectionChangeListeners.remove(listener);
        }

        void addOnImageDownloadedListener(OnImageDownloadedListener listener) {
            imageDownloadedListeners.add(listener);
        }

        void removeOnImageDownloadedListener(OnImageDownloadedListener listener) {
            imageDownloadedListeners.remove(listener);
        }

        void setOnLoginListener(OnLoginListener listener) {
            ConnectionService.this.loginListener = listener;
        }
    }

    @Nullable
    @Override
    public IBinder onBind(Intent intent) {
        return binder;
    }

    Handler mainThreadHandler;

    @Override
    public void onDestroy() {
        super.onDestroy();
        if (socket != null && !socket.isClosed()) {
            try {
                socket.close();
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
        Log.v(TAG, "Destroy instance (" + String.valueOf(this) + ")");
    }

    @Override
    public void onCreate() {
        super.onCreate();
        mainThreadHandler = new Handler();
        Log.v(TAG, "New instance (" + String.valueOf(this) + ")");
    }

    Thread inputListenerThread = new Thread(new Runnable() {
        @Override
        public void run() {
            try {
                InputStream inputStream = socket.getInputStream();
                /*try {
                    Thread.sleep(2000);
                }catch (InterruptedException e){}*/
                while (!Thread.interrupted()) {
                   /* try {
                        Thread.sleep(2000);
                    }catch (InterruptedException e){}
                    Log.v(TAG,"Available("+String.valueOf(Thread.currentThread().getId())+"): "+String.valueOf(inputStream.available()));
*/
                    /*while(inputStream.available() < 1){

                    }
                    String data = "";
                    while(inputStream.available()>0){
                        data += String.valueOf(inputStream.read())+ ", ";
                    }
                    Log.v(TAG,"Data: "+data);*/

                    /*char command = (char) inputStream.read();
                    Log.v(TAG, "Received: " + String.valueOf((int) command));*/
                    switch (inputStream.read()) {
                        case 0: {
                            Log.v(TAG, "Got answer to request " + String.valueOf(inputStream.read()) + ": " + String.valueOf(inputStream.read()));
                        }
                        break;
                        case 3: {
                            inputStream.read();//dismiss request code
                            char[] buffer = new char[32];
                            for (char vez = 0; vez < 24; vez++) {
                                buffer[vez] = (char) inputStream.read();
                            }
                            String id = String.copyValueOf(buffer, 0, 24);
                            for (char vez = 0; vez < 32; vez++) {
                                buffer[vez] = (char) inputStream.read();
                            }
                            String key = String.copyValueOf(buffer);

                            PreferenceManager.getDefaultSharedPreferences(ConnectionService.this).edit().putString("login_id", id).putString("login_key", key).apply();
                            if (loginListener != null) loginListener.onLogin();
                            //Log.v(TAG, "Logged in: " + id + " - " + key);
                        }
                        break;
                        case 4: {
                            inputStream.read();
                            if (inputStream.read() != 1) {
                                //TODO handle error logging in
                            }
                        }
                        break;
                        case 5: {
                            inputStream.read();//dismiss request code
                            char[] buffer = new char[24];
                            for (char vez = 0; vez < 24; vez++) {
                                buffer[vez] = (char) inputStream.read();
                            }
                            String id = String.copyValueOf(buffer);
                            char size = (char) inputStream.read();
                            buffer = new char[size];
                            for (char vez = 0; vez < size; vez++) {
                                buffer[vez] = (char) inputStream.read();
                            }
                            String name = String.copyValueOf(buffer);
                            size = (char) inputStream.read();
                            buffer = new char[size];
                            for (char vez = 0; vez < size; vez++) {
                                buffer[vez] = (char) inputStream.read();
                            }
                            String email = String.copyValueOf(buffer);

                            PreferenceManager.getDefaultSharedPreferences(ConnectionService.this).edit().putString("user_name", name).putString("user_email", email).putString("user_id", id).apply();
                            //Log.v(TAG, "Logged in: " + name + " - " + email + " - " + id);

                            //Temporary request profile image every time info is got
                            Bundle extras = new Bundle();
                            extras.putString(EXTRA_USER_ID, id);
                            sendRequest(ConnectionService.REQUEST_IMAGE, extras);
                        }
                        break;
                        case 6: {
                            inputStream.read();
                            char[] idBuffer = new char[24];
                            for (char vez = 0; vez < 24; vez++) {
                                idBuffer[vez] = (char) inputStream.read();
                            }
                            final String id = String.copyValueOf(idBuffer);
                            int size = inputStream.read() << 24 | inputStream.read() << 16 | inputStream.read() << 8 | inputStream.read();
                            //Log.v(TAG,"Image size: "+String.valueOf(size));
                            byte[] buffer = new byte[size];
                            for (char vez = 0; vez < size; vez++) {
                                buffer[vez] = (byte) inputStream.read();
                            }
                            final Bitmap result = BitmapFactory.decodeByteArray(buffer, 0, buffer.length);
                            File file = new File(getCacheDir().getPath() + "/images/profile/" + id);
                            file.getParentFile().mkdirs();
                            result.compress(Bitmap.CompressFormat.WEBP, 80, new FileOutputStream(file));
                            mainThreadHandler.post(new Runnable() {
                                @Override
                                public void run() {
                                    for (OnImageDownloadedListener listener : imageDownloadedListeners) {
                                        listener.onImageDownloaded(id, result);
                                    }
                                }
                            });
                            //Log.v(TAG,"Received image");
                        }
                        break;
                    }
                }
                socket.close();
            } catch (IOException e) {
                e.printStackTrace();
                try {
                    if (socket != null) socket.close();
                } catch (IOException e2) {
                    e2.printStackTrace();
                }
                /*mainThreadHandler.post(new Runnable() {
                    @Override
                    public void run() {
                        for (OnConnectionChangeListener listener : connectionChangeListeners) {
                            listener.onDisconnected();
                        }
                    }
                });*/
            }
        }
    });

    boolean proccesing = false;
    ArrayList<Integer> requestList = new ArrayList<>();
    ArrayList<Bundle> requestBundleList = new ArrayList<>();


    private void sendRequest(int request, Bundle extras) {
        requestList.add(request);
        requestBundleList.add(extras);
        if (!proccesing) {
            proccesing = true;
            new Thread(new Runnable() {
                @Override
                public void run() {
                    try {
                        if (socket == null || socket.isClosed()) {
                            mainThreadHandler.post(new Runnable() {
                                @Override
                                public void run() {
                                    for (OnConnectionChangeListener listener : connectionChangeListeners) {
                                        listener.onConnecting();
                                    }
                                }
                            });
                            //Log.v(TAG, "Connecting");
                            socket = new Socket();
                            socket.connect(new InetSocketAddress(SERVER_IP, SERVER_PORT), SERVER_TIMEOUT);
                            //Log.v(TAG, "Connected");
                            mainThreadHandler.post(new Runnable() {
                                @Override
                                public void run() {
                                    for (OnConnectionChangeListener listener : connectionChangeListeners) {
                                        listener.onConnected();
                                    }
                                }
                            });
                            inputListenerThread.start();
                            SharedPreferences preferences = PreferenceManager.getDefaultSharedPreferences(ConnectionService.this);
                            if (preferences.contains("login_id")) {//Identify app on server if already logged in
                                ByteBuffer buffer = ByteBuffer.allocate(32 + 24 + 2);
                                buffer.put(new byte[]{4, 0});
                                /*Log.v(TAG, "Login size: " + String.valueOf(preferences.getString("login_id", null).getBytes().length) + " - " + String.valueOf(preferences.getString("login_key", null).getBytes().length));
                                Log.v(TAG, "Login: " + preferences.getString("login_id", null) + " - " + preferences.getString("login_key", null));*/
                                buffer.put(preferences.getString("login_id", null).getBytes());
                                buffer.put(preferences.getString("login_key", null).getBytes());
                                socket.getOutputStream().write(buffer.array());
                            }
                        }
                        byte[] data = null;
                        while (requestList.size() > 0) {
                            switch (requestList.get(0)) {
                                case REQUEST_INFO: {
                                    data = new byte[]{0, 0};
                                }
                                break;
                                case REQUEST_LOGIN_GOOGLE: {
                                    //data = new byte[];
                                    String token = requestBundleList.get(0).getString(EXTRA_LOGIN_GOOGLE_TOKEN);
                                    ByteBuffer buffer = ByteBuffer.allocate(4 + token.length());
                                    buffer.put(new byte[]{3, 0});
                                    buffer.putShort((short) token.length());
                                    buffer.put(token.getBytes());
                                    data = buffer.array();
                                }
                                break;
                                case REQUEST_IMAGE: {
                                    final String id = requestBundleList.get(0).getString(EXTRA_USER_ID);
                                    File cachedFile = new File(getCacheDir().getPath() + "/images/profile/" + id);
                                    if (cachedFile.exists()) {//Send cached image or request download if not available
                                        final Bitmap result = BitmapFactory.decodeFile(cachedFile.getPath());
                                        mainThreadHandler.post(new Runnable() {
                                            @Override
                                            public void run() {
                                                for (OnImageDownloadedListener listener : imageDownloadedListeners) {
                                                    listener.onImageDownloaded(id, result);
                                                }
                                            }
                                        });
                                        Log.v(TAG, "Loaded cached image");
                                    } else {
                                        ByteBuffer buffer = ByteBuffer.allocate(2 + 24);
                                        buffer.put(new byte[]{6, 0});
                                        buffer.put(id.getBytes());
                                        data = buffer.array();
                                        Log.v(TAG, "Requested image");
                                    }
                                }
                                break;
                                default:
                                    data = new byte[0];
                                    break;
                            }
                            if (data != null) socket.getOutputStream().write(data);
                            requestList.remove(0);
                            requestBundleList.remove(0);
                        }
                    } catch (IOException e) {
                        e.printStackTrace();
                        try {
                            if (socket != null) socket.close();
                        } catch (IOException e2) {
                            e2.printStackTrace();
                        }
                        inputListenerThread.interrupt();
                        mainThreadHandler.post(new Runnable() {
                            @Override
                            public void run() {
                                for (OnConnectionChangeListener listener : connectionChangeListeners) {
                                    listener.onDisconnected();
                                }
                            }
                        });
                    }
                    proccesing = false;
                }
            }).start();
        }
    }
}
