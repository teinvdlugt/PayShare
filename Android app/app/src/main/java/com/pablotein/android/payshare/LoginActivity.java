package com.pablotein.android.payshare;

import android.app.ProgressDialog;
import android.content.ComponentName;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.ServiceConnection;
import android.os.Bundle;
import android.os.IBinder;
import android.preference.PreferenceManager;
import android.support.annotation.NonNull;
import android.support.design.widget.Snackbar;
import android.support.v7.app.AlertDialog;
import android.support.v7.app.AppCompatActivity;
import android.util.Log;
import android.view.View;
import android.widget.EditText;
import android.widget.Toast;

import com.google.android.gms.auth.api.Auth;
import com.google.android.gms.auth.api.signin.GoogleSignInAccount;
import com.google.android.gms.auth.api.signin.GoogleSignInOptions;
import com.google.android.gms.auth.api.signin.GoogleSignInResult;
import com.google.android.gms.common.ConnectionResult;
import com.google.android.gms.common.api.GoogleApiClient;

public class LoginActivity extends AppCompatActivity {

    private static final String TAG = "LoginActivity";

    GoogleApiClient googleApiClient;
    ProgressDialog progressDialog;

    private static final int REQUEST_LOGIN_GOOGLE = 1;

    ConnectionService.ConnectionServiceBinder connectionServiceBinder;
    ServiceConnection serviceConnection = new ServiceConnection() {
        @Override
        public void onServiceConnected(ComponentName name, IBinder service) {
            connectionServiceBinder = (ConnectionService.ConnectionServiceBinder) service;
        }

        @Override
        public void onServiceDisconnected(ComponentName name) {

        }
    };

    private View signInButton;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_login);
        signInButton = findViewById(R.id.sign_in_button);

        //Connect to connectionService
        Intent intent = new Intent(this, ConnectionService.class);
        bindService(intent, serviceConnection, Context.BIND_AUTO_CREATE);

        GoogleSignInOptions gso = new GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
                .requestIdToken("851535968316-cqvil0i6ej1mcgs3314bqv0k460i6j4f.apps.googleusercontent.com")//Api code
                .requestEmail()
                .requestProfile()
                .build();
        googleApiClient = new GoogleApiClient.Builder(LoginActivity.this)
                .enableAutoManage(LoginActivity.this, new GoogleApiClient.OnConnectionFailedListener() {
                    @Override
                    public void onConnectionFailed(@NonNull ConnectionResult connectionResult) {
                        Log.e(TAG, "Google API connection error");
                    }
                })
                .addApi(Auth.GOOGLE_SIGN_IN_API, gso)
                .build();

        //Experimental login system
        signInButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                progressDialog = new ProgressDialog(LoginActivity.this);
                progressDialog.setMessage(getString(R.string.loading));
                progressDialog.setCancelable(false);
                progressDialog.show();
                Intent signInIntent = Auth.GoogleSignInApi.getSignInIntent(googleApiClient);
                startActivityForResult(signInIntent, REQUEST_LOGIN_GOOGLE);
            }
        });
        signInButton.post(new Runnable() {
            @Override
            public void run() {
                checkTabletLayout();
            }
        });

        findViewById(R.id.change_ip_button).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                AlertDialog dialog = new AlertDialog.Builder(LoginActivity.this)
                        .setTitle("Set server IP")
                        .setView(R.layout.set_server_ip_dialog)
                        .setPositiveButton(R.string.ok, new DialogInterface.OnClickListener() {
                            @Override
                            public void onClick(DialogInterface dialog, int which) {
                                PreferenceManager.getDefaultSharedPreferences(LoginActivity.this).edit().putString("IP", ((EditText) ((AlertDialog) dialog).findViewById(R.id.server_ip)).getText().toString()).apply();
                                Toast.makeText(LoginActivity.this, "Changes will be applied on app restart", Toast.LENGTH_LONG).show();
                            }
                        })
                        .create();
                dialog.show();
                ((EditText) dialog.findViewById(R.id.server_ip)).setText(PreferenceManager.getDefaultSharedPreferences(LoginActivity.this).getString("IP", ""));
            }
        });
    }

    private void checkTabletLayout() {
        float maxWidth = getResources().getDimension(R.dimen.max_sign_in_button_width);
        if (signInButton.getWidth() > maxWidth) {
            signInButton.getLayoutParams().width = (int) maxWidth;
            signInButton.requestLayout();
        }
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == REQUEST_LOGIN_GOOGLE) {
            GoogleSignInResult result = Auth.GoogleSignInApi.getSignInResultFromIntent(data);
            if (result.isSuccess()) {
                GoogleSignInAccount acct = result.getSignInAccount();
                Log.v(TAG, "Login OK " + acct.getEmail() + " - " + acct.getIdToken());
                Bundle extras = new Bundle();
                extras.putString(ConnectionService.EXTRA_LOGIN_GOOGLE_TOKEN, acct.getIdToken());
                connectionServiceBinder.setOnLoginListener(new ConnectionService.OnLoginListener() {
                    @Override
                    public void onLogin() {
                        progressDialog.dismiss();
                        Intent intent = new Intent(LoginActivity.this,MainActivity.class);
                        startActivity(intent);
                        LoginActivity.this.finish();
                    }
                });
                connectionServiceBinder.sendRequest(ConnectionService.REQUEST_LOGIN_GOOGLE, extras);//Send token to server
            } else {
                progressDialog.dismiss();
                Log.v(TAG, "Login ERROR");
                Snackbar.make(findViewById(R.id.parentView), getString(com.pablotein.android.payshare.R.string.error_login_google), Snackbar.LENGTH_SHORT).show();
            }
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        unbindService(serviceConnection);
    }
}
