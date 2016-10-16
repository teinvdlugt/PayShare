package com.pablotein.android.payshare;

import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.os.Bundle;
import android.os.IBinder;
import android.support.annotation.Nullable;
import android.support.design.widget.Snackbar;
import android.support.v4.widget.SwipeRefreshLayout;
import android.support.v7.app.AppCompatActivity;
import android.support.v7.widget.LinearLayoutManager;
import android.support.v7.widget.RecyclerView;
import android.util.Log;
import android.view.View;

import java.util.ArrayList;

public class ListActivity extends AppCompatActivity implements ConnectionService.OnConnectionChangeListener, ServiceConnection, ConnectionService.OnListReceivedListener {

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
        Log.v("Service", "bound");
        connectionServiceBinder = (ConnectionService.ConnectionServiceBinder) service;
        connectionServiceBinder.addOnConnectionChangeListener(ListActivity.this);
        connectionServiceBinder.setOnListReceivedListener(this);
        Bundle extras = new Bundle();
        extras.putString(ConnectionService.EXTRA_LIST_ID, getIntent().getStringExtra(ConnectionService.EXTRA_LIST_ID));
        connectionServiceBinder.sendRequest(ConnectionService.REQUEST_GET_LIST_ITEMS, extras);
    }

    @Override
    public void onServiceDisconnected(ComponentName name) {
    }

    @Override
    public void onListsReceived(boolean available, ListRecyclerViewAdapter.ListSettings listSettings, ArrayList<ListRecyclerViewAdapter.ListRecyclerItem> items) {
        if (available) {
            ListRecyclerViewAdapter adapter = (ListRecyclerViewAdapter) recyclerView.getAdapter();
            adapter.setSettings(listSettings);
            adapter.setItems(items);
        } else {
            //TODO show error showing list
        }
        swipeRefreshLayout.setRefreshing(false);
    }

    RecyclerView recyclerView;
    SwipeRefreshLayout swipeRefreshLayout;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_list);
        //Connect to connectionService
        Intent intent = new Intent(this, ConnectionService.class);
        bindService(intent, this, Context.BIND_AUTO_CREATE);

        recyclerView = (RecyclerView) findViewById(R.id.listRecyclerView);
        recyclerView.setLayoutManager(new LinearLayoutManager(this));
        recyclerView.setAdapter(new ListRecyclerViewAdapter(this));

        swipeRefreshLayout = (SwipeRefreshLayout)findViewById(R.id.swipeRefreshLayout);
        swipeRefreshLayout.setOnRefreshListener(new SwipeRefreshLayout.OnRefreshListener() {
            @Override
            public void onRefresh() {
                Bundle extras = new Bundle();
                extras.putString(ConnectionService.EXTRA_LIST_ID, getIntent().getStringExtra(ConnectionService.EXTRA_LIST_ID));
                connectionServiceBinder.sendRequest(ConnectionService.REQUEST_GET_LIST_ITEMS, extras);
            }
        });
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        connectionServiceBinder.removeOnConnectionChangeListener(this);
        unbindService(this);
    }
}
