package com.pablotein.android.payshare;

import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.content.SharedPreferences;
import android.graphics.Bitmap;
import android.os.IBinder;
import android.preference.PreferenceManager;
import android.support.annotation.NonNull;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.telecom.Connection;
import android.util.Log;
import android.view.View;
import android.widget.ImageView;
import android.widget.TextView;

import com.google.android.gms.auth.api.Auth;
import com.google.android.gms.auth.api.signin.GoogleSignInAccount;
import com.google.android.gms.auth.api.signin.GoogleSignInOptions;
import com.google.android.gms.auth.api.signin.GoogleSignInResult;
import com.google.android.gms.common.ConnectionResult;
import com.google.android.gms.common.api.GoogleApiClient;

public class MainActivity extends AppCompatActivity {

    private static final String TAG = "MainActivity";

    ConnectionService.ConnectionServiceBinder connectionServiceBinder;
    ConnectionService.OnConnectionChangeListener connectionChangeListener = new ConnectionService.OnConnectionChangeListener() {
        @Override
        public void onConnected() {
            ((TextView) findViewById(R.id.testText)).setText("Connected");
        }

        @Override
        public void onDisconnected() {
            ((TextView) findViewById(R.id.testText)).setText("Disconnected");
        }

        @Override
        public void onConnecting() {
            ((TextView) findViewById(R.id.testText)).setText("Connecting");
        }
    };
    ConnectionService.OnImageDownloadedListener imageDownloadedListener = new ConnectionService.OnImageDownloadedListener() {
        @Override
        public void onImageDownloaded(String id, Bitmap image) {
            ((ImageView)findViewById(R.id.profileImage)).setImageBitmap(image);
            updateNameEmail(PreferenceManager.getDefaultSharedPreferences(MainActivity.this));//TODO solve, Quick fix for solving preference change listener not being called
        }
    };
    ServiceConnection serviceConnection = new ServiceConnection() {
        @Override
        public void onServiceConnected(ComponentName name, IBinder service) {
            connectionServiceBinder = (ConnectionService.ConnectionServiceBinder) service;
            connectionServiceBinder.addOnConnectionChangeListener(connectionChangeListener);
            connectionServiceBinder.addOnImageDownloadedListener(imageDownloadedListener);
            connectionServiceBinder.sendRequest(ConnectionService.REQUEST_INFO, null);//Request server version (as test)
            /*Bundle extras = new Bundle();
            extras.putString(ConnectionService.EXTRA_USER_ID,PreferenceManager.getDefaultSharedPreferences(MainActivity.this).getString("user_id",null));
            if(extras.getString(ConnectionService.EXTRA_USER_ID) != null)connectionServiceBinder.sendRequest(ConnectionService.REQUEST_IMAGE,extras);*/
        }

        @Override
        public void onServiceDisconnected(ComponentName name) {

        }
    };

    private void updateNameEmail(SharedPreferences sharedPreferences){
        String text = "Name: "+sharedPreferences.getString("user_name",null)+", email: "+sharedPreferences.getString("user_email",null);
        ((TextView)findViewById(R.id.infoText)).setText(text);
        Log.v(TAG,"UPDATE: "+text);
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        //Connect to connectionService
        Intent intent = new Intent(this, ConnectionService.class);
        bindService(intent, serviceConnection, Context.BIND_AUTO_CREATE);

        updateNameEmail(PreferenceManager.getDefaultSharedPreferences(this));

        PreferenceManager.getDefaultSharedPreferences(this).registerOnSharedPreferenceChangeListener(new SharedPreferences.OnSharedPreferenceChangeListener() {
            @Override
            public void onSharedPreferenceChanged(SharedPreferences sharedPreferences, String key) {
                    updateNameEmail(sharedPreferences);
            }
        });
        if(!PreferenceManager.getDefaultSharedPreferences(this).contains("login_id")/* || true*/){//Force login screen to show even if already logged for debugging purposes
            Intent startLoginIntent = new Intent(this,LoginActivity.class);
            startActivity(startLoginIntent);
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        connectionServiceBinder.removeOnConnectionChangeListener(connectionChangeListener);
        connectionServiceBinder.removeOnImageDownloadedListener(imageDownloadedListener);
        unbindService(serviceConnection);
    }
}
