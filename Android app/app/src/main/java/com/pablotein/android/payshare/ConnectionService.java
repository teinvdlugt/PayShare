package com.pablotein.android.payshare;

import android.app.Service;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Binder;
import android.os.Bundle;
import android.os.Handler;
import android.os.IBinder;
import android.preference.PreferenceManager;
import android.support.annotation.Nullable;
import android.util.Log;

import java.io.BufferedInputStream;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.Socket;
import java.nio.ByteBuffer;
import java.util.ArrayList;

public class ConnectionService extends Service {
    IBinder binder = new ConnectionServiceBinder();
    Socket socket;
    ArrayList<OnConnectionChangeListener> connectionChangeListeners = new ArrayList<>();
    OnLoginListener loginListener;

    private static String TAG = "ConnectionService", SERVER_IP = "192.168.1.36";
    private static int SERVER_PORT = 1234;

    public static final int REQUEST_INFO = 0, REQUEST_LOGIN_GOOGLE = 1;
    public static final String EXTRA_LOGIN_GOOGLE_TOKEN = "ExtraLoginGoogleToken";

    public interface OnConnectionChangeListener {
        void onConnected();

        void onDisconnected();
    }

    public interface OnLoginListener {
        void onLogin();
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

        void setOnLoginListener(OnLoginListener listener){
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
    public void onCreate() {
        super.onCreate();
        mainThreadHandler = new Handler();
    }

    Thread inputListenerThread = new Thread(new Runnable() {
        @Override
        public void run() {
            try {
                InputStream inputSream = socket.getInputStream();
                while(!Thread.interrupted()) {
                    char command = (char) inputSream.read();
                    Log.v(TAG, "Received: " + String.valueOf((int) command));
                    switch (command) {
                        case 0:
                            Log.v(TAG, "Got answer to request " + String.valueOf(inputSream.read()) + ": " + String.valueOf(inputSream.read()));
                            break;
                        case 2:
                            inputSream.read();//dismiss request code
                            char[] buffer = new char[32];
                            for (char vez = 0; vez < 24; vez++) {
                                buffer[vez] = (char) inputSream.read();
                            }
                            String id = String.copyValueOf(buffer, 0, 24);
                            for (char vez = 0; vez < 32; vez++) {
                                buffer[vez] = (char) inputSream.read();
                            }
                            String key = String.copyValueOf(buffer);

                            PreferenceManager.getDefaultSharedPreferences(ConnectionService.this).edit().putString("login_id", id).putString("login_key", key).apply();
                            loginListener.onLogin();
                            Log.v(TAG, "Logged in: " + id + " - " + key);
                            break;
                        case 3:
                            inputSream.read();
                            if (inputSream.read() != 1) {
                                //TODO handle error logging in
                            }
                            break;
                        case 4:
                            inputSream.read();//dismiss request code
                            char size = (char) inputSream.read();
                            buffer = new char[size];
                            for (char vez = 0; vez < size; vez++) {
                                buffer[vez] = (char) inputSream.read();
                            }
                            String name = String.copyValueOf(buffer);
                            size = (char) inputSream.read();
                            buffer = new char[size];
                            for (char vez = 0; vez < size; vez++) {
                                buffer[vez] = (char) inputSream.read();
                            }
                            String email = String.copyValueOf(buffer);

                            PreferenceManager.getDefaultSharedPreferences(ConnectionService.this).edit().putString("login_name", name).putString("login_email", email).apply();
                            Log.v(TAG, "Logged in: " + name + " - " + email);
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
                mainThreadHandler.post(new Runnable() {
                    @Override
                    public void run() {
                        for (OnConnectionChangeListener listener : connectionChangeListeners) {
                            listener.onDisconnected();
                        }
                    }
                });
            }
        }
    });

    private void sendRequest(final int request, final Bundle extras) {
        new Thread(new Runnable() {
            @Override
            public void run() {
                try {
                    if (socket == null || socket.isClosed()) {
                        Log.v(TAG,"Connecting");
                        socket = new Socket(SERVER_IP, SERVER_PORT);
                        Log.v(TAG,"Connected");
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
                        if(preferences.contains("login_id")) {
                            ByteBuffer buffer = ByteBuffer.allocate(32 + 24 + 2);
                            buffer.put(new byte[]{3, 0});
                            buffer.put(preferences.getString("login_id",null).getBytes());
                            buffer.put(preferences.getString("login_key",null).getBytes());
                            socket.getOutputStream().write(buffer.array());
                        }
                    }
                    byte[] data;
                    switch (request) {
                        case REQUEST_INFO:
                            data = new byte[]{0, 0};
                            break;
                        case REQUEST_LOGIN_GOOGLE:
                            //data = new byte[];
                            String token = extras.getString(EXTRA_LOGIN_GOOGLE_TOKEN);
                            ByteBuffer buffer = ByteBuffer.allocate(4+token.length());
                            buffer.put(new byte[]{2,0});
                            buffer.putShort((short)token.length());
                            buffer.put(token.getBytes());
                            data = buffer.array();
                            break;
                        default:
                            data = new byte[0];
                            break;
                    }
                    socket.getOutputStream().write(data);
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
            }
        }).start();
    }
}
