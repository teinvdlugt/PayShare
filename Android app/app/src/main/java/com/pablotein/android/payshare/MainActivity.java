package com.pablotein.android.payshare;

import android.app.Dialog;
import android.content.ComponentName;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.ServiceConnection;
import android.content.SharedPreferences;
import android.graphics.Bitmap;
import android.os.IBinder;
import android.preference.PreferenceManager;
import android.support.design.widget.FloatingActionButton;
import android.support.design.widget.Snackbar;
import android.support.v7.app.AlertDialog;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.support.v7.widget.LinearLayoutManager;
import android.support.v7.widget.RecyclerView;
import android.text.Editable;
import android.text.TextWatcher;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.TextView;

import java.util.ArrayList;

public class MainActivity extends AppCompatActivity {

    private static final String TAG = "MainActivity";

    ConnectionService.ConnectionServiceBinder connectionServiceBinder;
    ConnectionService.OnConnectionChangeListener connectionChangeListener = new ConnectionService.OnConnectionChangeListener() {
        @Override
        public void onConnected() {
            ((TextView) findViewById(R.id.testText)).setText("Connected");
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
            ((ImageView) findViewById(R.id.profileImage)).setImageBitmap(image);
            updateNameEmail(PreferenceManager.getDefaultSharedPreferences(MainActivity.this));//TODO solve, Quick fix for solving preference change listener not being called
        }
    };
    ServiceConnection serviceConnection = new ServiceConnection() {
        @Override
        public void onServiceConnected(ComponentName name, IBinder service) {
            connectionServiceBinder = (ConnectionService.ConnectionServiceBinder) service;
            connectionServiceBinder.addOnConnectionChangeListener(connectionChangeListener);
            connectionServiceBinder.addOnImageDownloadedListener(imageDownloadedListener);
            connectionServiceBinder.setOnListsReceivedListener(new ConnectionService.OnListsReceivedListener() {
                @Override
                public void onListsReceived(ArrayList<ListRecyclerViewAdapter.ListRecyclerItem> lists) {
                    ((ListRecyclerViewAdapter) listRecyclerView.getAdapter()).setLists(lists);
                }
            });
            connectionServiceBinder.sendRequest(ConnectionService.REQUEST_INFO, null);//Request server version (as test)
            if (PreferenceManager.getDefaultSharedPreferences(MainActivity.this).getString("user_id", null) != null)
                connectionServiceBinder.sendRequest(ConnectionService.REQUEST_GET_LISTS, null);//Request list of lists if user is logged
            /*Bundle extras = new Bundle();
            extras.putString(ConnectionService.EXTRA_USER_ID,PreferenceManager.getDefaultSharedPreferences(MainActivity.this).getString("user_id",null));
            if(extras.getString(ConnectionService.EXTRA_USER_ID) != null)connectionServiceBinder.sendRequest(ConnectionService.REQUEST_IMAGE,extras);*/
        }

        @Override
        public void onServiceDisconnected(ComponentName name) {

        }
    };

    private void updateNameEmail(SharedPreferences sharedPreferences) {
        String text = "Name: " + sharedPreferences.getString("user_name", null) + ", email: " + sharedPreferences.getString("user_email", null);
        ((TextView) findViewById(R.id.infoText)).setText(text);
        Log.v(TAG, "UPDATE: " + text);
    }

    RecyclerView listRecyclerView;
    FloatingActionButton addListFAB;

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
        if (!PreferenceManager.getDefaultSharedPreferences(this).contains("login_id")/* || true*/) {//Force login screen to show even if already logged for debugging purposes
            Intent startLoginIntent = new Intent(this, LoginActivity.class);
            startActivity(startLoginIntent);
        }
        listRecyclerView = (RecyclerView) findViewById(R.id.listRecyclerView);
        listRecyclerView.setLayoutManager(new LinearLayoutManager(this));
        listRecyclerView.setAdapter(new ListRecyclerViewAdapter());
        addListFAB = (FloatingActionButton) findViewById(R.id.addListFAB);
        addListFAB.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                //View view = LayoutInflater.from(MainActivity.this).inflate(R.layout.new_list_dialog,)
                final AlertDialog dialog = new AlertDialog.Builder(MainActivity.this)
                        .setView(R.layout.new_list_dialog)
                        .setTitle(R.string.new_list)
                        .setPositiveButton(R.string.ok, new DialogInterface.OnClickListener() {
                            @Override
                            public void onClick(DialogInterface dialog, int which) {
                                Bundle extras = new Bundle();
                                extras.putString(ConnectionService.EXTRA_NAME, ((EditText) ((Dialog) dialog).findViewById(R.id.new_list_name)).getText().toString());
                                connectionServiceBinder.sendRequest(ConnectionService.REQUEST_NEW_LIST, extras);
                            }
                        })
                        .create();
                dialog.show();
                final Button positiveButton = dialog.getButton(DialogInterface.BUTTON_POSITIVE);
                positiveButton.setClickable(false);
                positiveButton.setEnabled(false);
                ((EditText) dialog.findViewById(R.id.new_list_name)).addTextChangedListener(new TextWatcher() {
                    @Override
                    public void beforeTextChanged(CharSequence s, int start, int count, int after) {

                    }

                    @Override
                    public void onTextChanged(CharSequence s, int start, int before, int count) {
                        if (s.length() > 0 && s.length() < 100) {
                            positiveButton.setClickable(true);
                            positiveButton.setEnabled(true);
                        } else {
                            positiveButton.setClickable(false);
                            positiveButton.setEnabled(false);
                        }
                    }

                    @Override
                    public void afterTextChanged(Editable s) {

                    }
                });
            }
        });
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        connectionServiceBinder.removeOnConnectionChangeListener(connectionChangeListener);
        connectionServiceBinder.removeOnImageDownloadedListener(imageDownloadedListener);
        unbindService(serviceConnection);
    }
}
