package au.smap.smapfingerprintreader;

import android.app.Activity;
import android.content.ClipData;
import android.content.Intent;
import android.content.SharedPreferences;
import android.net.Uri;
import android.os.Bundle;

import androidx.appcompat.app.AppCompatActivity;

import android.widget.TextView;

import androidx.lifecycle.Observer;
import androidx.lifecycle.ViewModelProvider;
import androidx.navigation.ui.AppBarConfiguration;
import androidx.preference.PreferenceManager;

import au.smap.smapfingerprintreader.application.FingerprintReader;
import au.smap.smapfingerprintreader.model.ScannerViewModel;
import au.smap.smapfingerprintreader.scanners.DemoScanner;
import au.smap.smapfingerprintreader.scanners.MFS500Scanner;
import au.smap.smapfingerprintreader.scanners.Scanner;

public class ScanActivity extends AppCompatActivity {

    AppBarConfiguration appBarConfiguration;
    FingerprintReader app;
    Scanner scanner;
    String currentState;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        app = FingerprintReader.getInstance();
        setContentView(R.layout.activity_scan);
        app.logView = (TextView) findViewById(R.id.log);
        app.clearLogs();
        currentState = ScannerViewModel.NOSTATE;

        /*
         * Get the Intent that started the scan activity
         * Get the parameters
         */
        Intent intent = getIntent();
        String type = intent.getStringExtra("type");
        app.minQuality = 20;
        app.timeOut = 10000;



        /*
         * Create Observers
         *
         */
        app.model = new ViewModelProvider(this).get(ScannerViewModel.class);

        // Observe scanner state
        app.model.getScannerState().observe(this, state -> {

            if(currentState.equals(state)) {
                app.setLogs("Event " + state + " received but already in this state", false);
                return;
            } else {
                currentState = state;

                if (state.equals(ScannerViewModel.DISCONNECTED)) {
                    app.setLogs("Disconnected: ", false);
                    app.setLogs("Connect the device", false);
                } else if (state.equals(ScannerViewModel.CONNECTED)) {
                    if(scanner.isConnected()) {
                        scanner.startCapture(app.minQuality, app.timeOut);
                    } else {
                        // Something went wrong
                        app.setLogs("Received connected event but device is not connected", false);
                        currentState = ScannerViewModel.DISCONNECTED;
                    }
                } else if (state.equals("scanning")) {
                    app.setLogs("Scanning: ", false);
                } else {
                    app.setLogs("Unknown scanner state: " + state, true);
                }
            }

        });

        // Observe the image live data
        app.model.getImage().observe(this, uri -> {
            app.setLogs("Image changed called: " + uri.toString(), false);

            // Return the results
            Intent returnIntent = new Intent();
            returnIntent.setClipData(ClipData.newRawUri("fpr.png", uri));
            returnIntent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);

            setResult(Activity.RESULT_OK, returnIntent);
            finish();
        });

        /*
         * Set up the scanner
         */
        SharedPreferences sharedPreferences = PreferenceManager.getDefaultSharedPreferences(this);
        String scannerName = sharedPreferences.getString("scanner", "Demo");
        scanner = getScanner(scannerName);
        app.setLogs("Connecting scanner: " + scannerName, false);
        scanner.connect();
        if(scanner.isConnected()) {
            currentState = ScannerViewModel.CONNECTED;
            scanner.startCapture(app.minQuality, app.timeOut);
        } else {
            currentState = ScannerViewModel.DISCONNECTED;
        }

    }

    @Override
    protected void onPause() {
        super.onPause();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        scanner.destroy();
    }

    @Override
    public boolean onSupportNavigateUp() {
         return super.onSupportNavigateUp();
    }

    private Scanner getScanner(String name) {
        if(name.equals("MFS500")) {
            return new MFS500Scanner(getApplicationContext());
        } else  if(name.equals("MFS100")) {
            app.setLogs("Scanner not supported: " + name, true);
            return null;
        } else {
            return new DemoScanner(getApplicationContext());
        }
    }

}