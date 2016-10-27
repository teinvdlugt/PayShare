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
import java.util.Date;

public class ConnectionService extends Service {
    IBinder binder = new ConnectionServiceBinder();
    Socket socket;
    ArrayList<OnConnectionChangeListener> connectionChangeListeners = new ArrayList<>();
    ArrayList<OnImageDownloadedListener> imageDownloadedListeners = new ArrayList<>();
    OnLoginListener loginListener;
    OnListsReceivedListener listsReceivedListener;
    OnListReceivedListener listReceivedListener;

    private static String TAG = "ConnectionService";
    private static int SERVER_PORT = 1234, SERVER_TIMEOUT = 2000;
    private String SERVER_IP = "192.168.1.35";

    public static final int REQUEST_INFO = 0, REQUEST_LOGIN_GOOGLE = 1, REQUEST_GET_IMAGE = 6, REQUEST_GET_LISTS = 7, REQUEST_NEW_LIST = 8, REQUEST_GET_LIST_ITEMS = 9;
    public static final String EXTRA_LOGIN_GOOGLE_TOKEN = "ExtraLoginGoogleToken", EXTRA_USER_ID = "ExtraUserID", EXTRA_LIST_ID = "ExtraListId", EXTRA_NAME = "ExtraName";

    public interface OnConnectionChangeListener {
        void onConnected();

        void onDisconnected(Runnable retryRunnable);

        void onConnecting();
    }

    public interface OnListsReceivedListener {
        void onListsReceived(ArrayList<ListsRecyclerViewAdapter.ListRecyclerItem> lists);
    }

    public interface OnListReceivedListener {
        void onListsReceived(boolean available, ListRecyclerViewAdapter.ListSettings listSettings, ArrayList<ListRecyclerViewAdapter.ListRecyclerItem> items);
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

        void setOnListsReceivedListener(OnListsReceivedListener listener) {
            ConnectionService.this.listsReceivedListener = listener;
        }

        void setOnListReceivedListener(OnListReceivedListener listener) {
            ConnectionService.this.listReceivedListener = listener;
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
        SERVER_IP = PreferenceManager.getDefaultSharedPreferences(this).getString("IP", SERVER_IP);
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
                        case 2: {
                            socket.close();
                            mainThreadHandler.post(new Runnable() {
                                @Override
                                public void run() {
                                    for (OnConnectionChangeListener listener : connectionChangeListeners) {
                                        listener.onDisconnected(null);
                                    }
                                }
                            });
                            Thread.currentThread().interrupt();
                        }
                        break;
                        case 3: {
                            inputStream.read();//dismiss request code
                            byte[] buffer = new byte[32];
                            for (char vez = 0; vez < 24; vez++) {
                                buffer[vez] = (byte) inputStream.read();
                            }
                            String id = new String(buffer, 0, 24, "UTF-8");
                            for (char vez = 0; vez < 32; vez++) {
                                buffer[vez] = (byte) inputStream.read();
                            }
                            String key = new String(buffer, "UTF-8");
                            PreferenceManager.getDefaultSharedPreferences(ConnectionService.this).edit().putString("login_id", id).putString("login_key", key).apply();
                            if (loginListener != null) loginListener.onLogin();
                            //Log.v(TAG, "Logged in: " + id + " - " + key);
                        }
                        break;
                        case 4: {
                            inputStream.read();
                            int data = inputStream.read();
                            if (data != 1) {
                                PreferenceManager.getDefaultSharedPreferences(ConnectionService.this).edit().clear().apply();
                                Intent startLoginIntent = new Intent(ConnectionService.this, LoginActivity.class);
                                startActivity(startLoginIntent);
                            }
                        }
                        break;
                        case 5: {
                            inputStream.read();//dismiss request code
                            byte[] buffer = new byte[24];
                            for (char vez = 0; vez < 24; vez++) {
                                buffer[vez] = (byte) inputStream.read();
                            }
                            String id = new String(buffer, "UTF-8");
                            char size = (char) inputStream.read();
                            buffer = new byte[size];
                            for (char vez = 0; vez < size; vez++) {
                                buffer[vez] = (byte) inputStream.read();
                            }
                            String name = new String(buffer, "UTF-8");
                            size = (char) inputStream.read();
                            buffer = new byte[size];
                            for (char vez = 0; vez < size; vez++) {
                                buffer[vez] = (byte) inputStream.read();
                            }
                            String email = new String(buffer, "UTF-8");

                            PreferenceManager.getDefaultSharedPreferences(ConnectionService.this).edit().putString("user_name", name).putString("user_email", email).putString("user_id", id).apply();
                            //Log.v(TAG, "Logged in: " + name + " - " + email + " - " + id);

                            //Temporary request profile image every time info is got
                            Bundle extras = new Bundle();
                            extras.putString(EXTRA_USER_ID, id);
                            sendRequest(ConnectionService.REQUEST_GET_IMAGE, extras);
                        }
                        break;
                        case 6: {
                            inputStream.read();
                            byte[] idBuffer = new byte[24];
                            for (char vez = 0; vez < 24; vez++) {
                                idBuffer[vez] = (byte) inputStream.read();
                            }
                            final String id = new String(idBuffer, "UTF-8");
                            int size = inputStream.read() << 24 | inputStream.read() << 16 | inputStream.read() << 8 | inputStream.read();
                            //Log.v(TAG,"Image size: "+String.valueOf(size));
                            byte[] buffer = new byte[size];
                            for (char vez = 0; vez < size; vez++) {
                                buffer[vez] = (byte) inputStream.read();
                            }
                            if (buffer.length > 0) {
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
                            }
                            Log.v(TAG, "Received image");
                        }
                        break;
                        case 7: {
                            inputStream.read();
                            final ArrayList<ListsRecyclerViewAdapter.ListRecyclerItem> list = new ArrayList<>();
                            int itemsCount = inputStream.read();
                            for (int vez = 0; vez < itemsCount; vez++) {
                                byte[] idBuffer = new byte[24];
                                for (int vez1 = 0; vez1 < 24; vez1++) {
                                    idBuffer[vez1] = (byte) inputStream.read();
                                }
                                int nameSize = inputStream.read();
                                byte[] nameBuffer = new byte[nameSize];
                                for (int vez1 = 0; vez1 < nameSize; vez1++) {
                                    nameBuffer[vez1] = (byte) inputStream.read();
                                }
                                list.add(new ListsRecyclerViewAdapter.ListRecyclerItem(new String(nameBuffer, "UTF-8"), new String(idBuffer)));
                            }
                            Log.v(TAG, "Processed list, items: " + String.valueOf(itemsCount));
                            mainThreadHandler.post(new Runnable() {
                                @Override
                                public void run() {
                                    listsReceivedListener.onListsReceived(list);
                                }
                            });
                        }
                        break;
                        case 9: {
                            inputStream.read();
                            if (inputStream.read() == 1) {
                                int listNameSize = inputStream.read();
                                byte[] listNameBuffer = new byte[listNameSize];
                                inputStream.read(listNameBuffer, 0, listNameSize);

                                boolean isPublic = inputStream.read() == 1;

                                byte[] currencyBuffer = new byte[3];
                                inputStream.read(currencyBuffer, 0, 3);
                                final ListRecyclerViewAdapter.ListSettings settings = new ListRecyclerViewAdapter.ListSettings(new String(listNameBuffer, "UTF-8"), isPublic, new String(currencyBuffer, "UTF-8"));

                                final ArrayList<ListRecyclerViewAdapter.ListRecyclerItem> list = new ArrayList<>();
                                int itemsCount = inputStream.read();
                                for (int vez = 0; vez < itemsCount; vez++) {
                                    byte[] byidBuffer = new byte[24];
                                    inputStream.read(byidBuffer, 0, 24);

                                    byte[] idBuffer = new byte[24];
                                    inputStream.read(idBuffer, 0, 24);

                                    int nameSize = inputStream.read();
                                    byte[] nameBuffer = new byte[nameSize];
                                    inputStream.read(nameBuffer, 0, nameSize);

                                    float price = Float.intBitsToFloat(inputStream.read() << 24 | inputStream.read() << 16 | inputStream.read() << 8 | inputStream.read());
                                    byte amount = (byte) inputStream.read();

                                    long UNIXDate = inputStream.read() << 24 | inputStream.read() << 16 | inputStream.read() << 8 | inputStream.read();
                                    Date createdDate = new Date(UNIXDate*1000);

                                    list.add(new ListRecyclerViewAdapter.ListRecyclerItem(new String(nameBuffer, "UTF-8"), new String(idBuffer, "UTF-8"), new String(byidBuffer, "UTF-8"), price, amount, createdDate));
                                    Log.v("Item", new String(nameBuffer, "UTF-8") + " - " + new String(byidBuffer, "UTF-8") + " - " + new String(idBuffer, "UTF-8") + " - " + String.valueOf(price) + " - " + String.valueOf(amount));
                                    //list.add(new ListRecyclerViewAdapter.ListRecyclerItem(new String(nameBuffer, "UTF-8"), new String(idBuffer)));
                                }
                                mainThreadHandler.post(new Runnable() {
                                    @Override
                                    public void run() {
                                        listReceivedListener.onListsReceived(true, settings, list);
                                    }
                                });
                            } else {
                                mainThreadHandler.post(new Runnable() {
                                    @Override
                                    public void run() {
                                        listReceivedListener.onListsReceived(false, null, null);
                                    }
                                });
                            }
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

    Thread outputThread = new Thread(new sendRunnable());


    private void sendRequest(int request, Bundle extras) {
        requestList.add(request);
        requestBundleList.add(extras);
        if (!proccesing) {
            proccesing = true;
            outputThread.start();
        }
    }

    private class sendRunnable implements Runnable {
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
                        case REQUEST_GET_IMAGE: {
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
                        case REQUEST_GET_LISTS: {
                            data = new byte[]{7, 0};
                        }
                        break;
                        case REQUEST_NEW_LIST: {
                            byte[] name = requestBundleList.get(0).getString(EXTRA_NAME).getBytes("UTF-8");
                            ByteBuffer buffer = ByteBuffer.allocate(4 + name.length);
                            buffer.put(new byte[]{8, 0});
                            buffer.putShort((short) name.length);
                            buffer.put(name);
                            data = buffer.array();
                        }
                        break;
                        case REQUEST_GET_LIST_ITEMS: {
                            ByteBuffer buffer = ByteBuffer.allocate(2 + 24);
                            buffer.put(new byte[]{9, 0});
                            buffer.put(requestBundleList.get(0).getString(EXTRA_LIST_ID).getBytes("UTF-8"));
                            data = buffer.array();
                        }
                        break;
                        default: {
                            data = new byte[0];
                        }
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

                final ArrayList<Integer> copyRequestList = new ArrayList<>(requestList);
                final ArrayList<Bundle> copyRequestBundleList = new ArrayList<>(requestBundleList);
                while (requestList.size() > 0) {
                    requestList.remove(0);
                    requestBundleList.remove(0);
                }
                mainThreadHandler.post(new Runnable() {
                    @Override
                    public void run() {
                        Runnable retryRunnable = new Runnable() {
                            @Override
                            public void run() {
                                requestList.addAll(copyRequestList);
                                requestBundleList.addAll(copyRequestBundleList);
                                outputThread.start();
                            }
                        };
                        for (OnConnectionChangeListener listener : connectionChangeListeners) {
                            listener.onDisconnected(retryRunnable);
                        }
                    }
                });
            }
            proccesing = false;

        }
    }
}
