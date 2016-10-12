package com.pablotein.android.payshare;

import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.os.Bundle;
import android.os.IBinder;
import android.os.PersistableBundle;
import android.support.design.widget.Snackbar;
import android.support.v7.app.AppCompatActivity;
import android.util.Log;
import android.view.View;

import java.util.List;

public class ListActivity extends AppCompatActivity implements ConnectionService.OnConnectionChangeListener, ServiceConnection {

    ConnectionService.ConnectionServiceBinder connectionServiceBinder;

    @Override
    public void onConnected() {
    }

    @Override
    public void onDisconnected(final Runnable retryRunnable) {
        Snackbar snackbar = Snackbar.make(findViewById(R.id.parent), R.string.connection_error, Snackbar.LENGTH_LONG);
        if (retryRunnable != null)
            snackbar.setAction(R.string.retry, new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    retryRunnable.run();
                }
            });
        snackbar.show();
    }

    @Override
    public void onConnecting() {
    }

    @Override
    public void onServiceConnected(ComponentName name, IBinder service) {
        connectionServiceBinder = (ConnectionService.ConnectionServiceBinder) service;
        connectionServiceBinder.addOnConnectionChangeListener(ListActivity.this);
        Bundle extras = new Bundle();
        extras.putString(ConnectionService.EXTRA_LIST_ID, getIntent().getStringExtra(ConnectionService.EXTRA_LIST_ID));
        connectionServiceBinder.sendRequest(ConnectionService.REQUEST_GET_LIST_ITEMS, extras);
    }

    @Override
    public void onServiceDisconnected(ComponentName name) {
    }

    @Override
    public void onCreate(Bundle savedInstanceState, PersistableBundle persistentState) {
        super.onCreate(savedInstanceState, persistentState);
    }

    @Override
    protected void onResume() {
        super.onResume();
        //Connect to connectionService
        Intent intent = new Intent(this, ConnectionService.class);
        bindService(intent, this, Context.BIND_AUTO_CREATE);
    }

    @Override
    protected void onPause() {
        super.onPause();
        connectionServiceBinder.removeOnConnectionChangeListener(this);
        unbindService(this);
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
    }
}
