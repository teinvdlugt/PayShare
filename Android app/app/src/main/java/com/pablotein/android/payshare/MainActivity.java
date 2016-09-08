package com.pablotein.android.payshare;

import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.os.IBinder;
import android.preference.PreferenceManager;
import android.support.annotation.NonNull;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.telecom.Connection;
import android.util.Log;
import android.view.View;
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
    };
    ServiceConnection serviceConnection = new ServiceConnection() {
        @Override
        public void onServiceConnected(ComponentName name, IBinder service) {
            connectionServiceBinder = (ConnectionService.ConnectionServiceBinder) service;
            connectionServiceBinder.addOnConnectionChangeListener(connectionChangeListener);
            connectionServiceBinder.sendRequest(ConnectionService.REQUEST_INFO, null);//Send request to check connection
        }

        @Override
        public void onServiceDisconnected(ComponentName name) {

        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        //Connect to connectionService
        Intent intent = new Intent(this, ConnectionService.class);
        bindService(intent, serviceConnection, Context.BIND_AUTO_CREATE);

        if(PreferenceManager.getDefaultSharedPreferences(this).getString("login_id",null) == null || true){//Force login screen to show even if already logged for debugging purposes
            Intent startLoginIntent = new Intent(this,LoginActivity.class);
            startActivity(startLoginIntent);
        }
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == 0) {
            GoogleSignInResult result = Auth.GoogleSignInApi.getSignInResultFromIntent(data);
            if (result.isSuccess()) {
                GoogleSignInAccount acct = result.getSignInAccount();
                Log.v(TAG, "Login OK " + acct.getEmail() + " - " + acct.getIdToken());
                Bundle extras = new Bundle();
                extras.putString(ConnectionService.EXTRA_LOGIN_GOOGLE_TOKEN, acct.getIdToken());
                connectionServiceBinder.sendRequest(ConnectionService.REQUEST_LOGIN_GOOGLE, extras);
                // Signed in successfully, show authenticated UI.
                /*GoogleSignInAccount acct = result.getSignInAccount();
                mStatusTextView.setText(getString(R.string.signed_in_fmt, acct.getDisplayName()));
                updateUI(true);*/
            } else {
                Log.v(TAG, "Login ERROR");
                // Signed out, show unauthenticated UI.
                //updateUI(false);
            }
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        connectionServiceBinder.removeOnConnectionChangeListener(connectionChangeListener);
        unbindService(serviceConnection);
    }
}
