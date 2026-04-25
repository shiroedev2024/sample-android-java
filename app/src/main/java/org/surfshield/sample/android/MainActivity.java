package org.surfshield.sample.android;

import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextUtils;
import android.text.TextWatcher;
import android.util.Log;
import android.view.View;
import android.widget.Toast;

import androidx.activity.EdgeToEdge;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.RequiresApi;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;

import com.github.shiroedev2024.leaf.android.library.LeafException;
import com.github.shiroedev2024.leaf.android.library.LeafVPNService;
import com.github.shiroedev2024.leaf.android.library.ServiceManagement;
import com.github.shiroedev2024.leaf.android.library.delegate.AssetsCallback;
import com.github.shiroedev2024.leaf.android.library.delegate.LeafListener;
import com.github.shiroedev2024.leaf.android.library.delegate.ServiceListener;
import com.github.shiroedev2024.leaf.android.library.delegate.SubscriptionCallback;

import org.surfshield.sample.android.BuildConfig;
import org.surfshield.sample.android.databinding.ActivityMainBinding;

import java.util.Map;

public class MainActivity extends AppCompatActivity implements ServiceListener {

    private static final String TAG = "MainActivity";
    private static final String CLIENT_ID_KEY = "client_id";

    private SharedPreferences sharedPreferences;
    private ActivityMainBinding binding;
    private final ServiceManagement sm = ServiceManagement.getInstance();

    private final ActivityResultLauncher<Intent> vpnConsentLauncher =
            registerForActivityResult(new ActivityResultContracts.StartActivityForResult(),
                    result -> {
                        if (result.getResultCode() == RESULT_OK) {
                            startVpnAfterConsent();
                        } else {
                            showToast("VPN permission denied");
                            updateUi(UIState.DISCONNECTED);
                        }
                    });

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);

        binding = ActivityMainBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());
        ViewCompat.setOnApplyWindowInsetsListener(binding.getRoot(), (v, insets) -> {
            Insets systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom);
            return insets;
        });

        sharedPreferences = getSharedPreferences("main", MODE_PRIVATE);
        checkAndRequestNotificationPermission();

        // Listen for core service binding
        sm.addServiceListener(this);

        // Listen for VPN lifecycle events
        sm.addLeafListener(leafListener);

        loadClientId();
        updateUi(UIState.DISCONNECTED);

        binding.clientIdEditText.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
            @Override public void onTextChanged(CharSequence s, int start, int before, int count) { saveClientId(); }
            @Override public void afterTextChanged(Editable s) {}
        });

        binding.fetchConnectButton.setOnClickListener(v -> {
            String clientId = binding.clientIdEditText.getText().toString().trim();
            if (TextUtils.isEmpty(clientId)) {
                showToast("Please enter a Client ID");
                return;
            }

            if (sm.isServiceDead()) {
                sm.bindService(this);
                showToast("Binding service, please wait...");
                return;
            }

            updateUi(UIState.LOADING);

            // Step 1: Fetch subscription document
            sm.updateSubscription(clientId, new SubscriptionCallback() {
                @Override
                public void onSubscriptionUpdating() {
                    Log.d(TAG, "Fetching subscription...");
                }

                @Override
                public void onSubscriptionSuccess() {
                    Log.d(TAG, "Subscription fetched successfully.");
                    updateGeoAssets();
                }

                @Override
                public void onSubscriptionFailure(@NonNull LeafException e) {
                    Log.e(TAG, "Subscription failed", e);
                    showToast("Subscription Failed: " + e.getMessage());
                    updateUi(UIState.DISCONNECTED);
                }
            });
        });

        binding.disconnectButton.setOnClickListener(v -> {
            updateUi(UIState.LOADING);
            sm.stopLeaf();
        });
    }

    // Step 2: Ensure GeoIP/Geosite are up to date
    private void updateGeoAssets() {
        try {
            String[] parts = BuildConfig.VERSION_NAME.split("\\.");
            int major = parts.length > 0 ? Integer.parseInt(parts[0]) : 1;
            int minor = parts.length > 1 ? Integer.parseInt(parts[1]) : 0;
            int patch = parts.length > 2 ? Integer.parseInt(parts[2]) : 0;

            sm.updateAssets(major, minor, patch, new AssetsCallback() {
                @Override
                public void onUpdateSuccess() {
                    Log.d(TAG, "Assets updated successfully.");
                    requestVpnConsent();
                }

                @Override
                public void onUpdateFailed(@Nullable LeafException e) {
                    Log.e(TAG, "Asset update failed", e);
                    showToast("Asset Update Failed: " + (e != null ? e.getMessage() : "Unknown"));
                    updateUi(UIState.DISCONNECTED);
                }
            });
        } catch (Exception e) {
            Log.e(TAG, "Failed to parse version for assets", e);
            requestVpnConsent(); // Fallback if parsing fails
        }
    }

    // Step 3: Ask the OS for VPN routing permission
    private void requestVpnConsent() {
        Intent intent = LeafVPNService.prepare(this);
        if (intent != null) {
            vpnConsentLauncher.launch(intent);
        } else {
            startVpnAfterConsent();
        }
    }

    // Step 4: Verify files and start the core
    private void startVpnAfterConsent() {
        try {
            sm.verifyFileIntegrity();
            sm.startLeaf();
        } catch (LeafException e) {
            Log.e(TAG, "Failed to verify integrity or start Leaf", e);
            showToast("Failed to start: " + e.getMessage());
            updateUi(UIState.DISCONNECTED);
        }
    }

    private final LeafListener leafListener = new LeafListener() {
        @Override
        public void onStarting() {
            Log.d(TAG, "Leaf is starting...");
            runOnUiThread(() -> updateUi(UIState.LOADING));
        }

        @Override
        public void onStartSuccess() {
            Log.d(TAG, "Leaf started successfully");
            runOnUiThread(() -> updateUi(UIState.CONNECTED));
        }

        @Override
        public void onStartFailed(@Nullable String msg) {
            Log.e(TAG, "Leaf start failed: " + msg);
            runOnUiThread(() -> {
                showToast("Failed to start: " + msg);
                updateUi(UIState.DISCONNECTED);
            });
        }

        @Override
        public void onReloadSuccess() {
            Log.d(TAG, "Leaf reloaded successfully");
            runOnUiThread(() -> showToast("Config reloaded"));
        }

        @Override
        public void onReloadFailed(@Nullable String msg) {
            Log.e(TAG, "Leaf reload failed: " + msg);
            runOnUiThread(() -> showToast("Reload failed: " + msg));
        }

        @Override
        public void onStopSuccess() {
            Log.d(TAG, "Leaf stopped successfully");
            runOnUiThread(() -> updateUi(UIState.DISCONNECTED));
        }

        @Override
        public void onStopFailed(@Nullable String msg) {
            Log.e(TAG, "Leaf stop failed: " + msg);
            runOnUiThread(() -> {
                showToast("Failed to stop: " + msg);
                updateUi(UIState.CONNECTED); // Likely still running
            });
        }
    };

    @Override
    protected void onStart() {
        super.onStart();
        sm.bindService(this);
    }

    @Override
    protected void onStop() {
        super.onStop();
        sm.unbindService(this);
    }

    @Override
    protected void onDestroy() {
        sm.removeLeafListener(leafListener);
        sm.removeServiceListener(this);
        super.onDestroy();
    }

    private enum UIState {
        LOADING,
        CONNECTED,
        DISCONNECTED
    }

    private void updateUi(UIState state) {
        switch (state) {
            case LOADING:
                binding.loading.setVisibility(View.VISIBLE);
                binding.fetchConnectButton.setEnabled(false);
                binding.disconnectButton.setEnabled(false);
                binding.clientIdEditText.setEnabled(false);
                break;
            case CONNECTED:
                binding.loading.setVisibility(View.GONE);
                binding.fetchConnectButton.setEnabled(false);
                binding.disconnectButton.setEnabled(true);
                binding.clientIdEditText.setEnabled(false);
                break;
            case DISCONNECTED:
                binding.loading.setVisibility(View.GONE);
                binding.fetchConnectButton.setEnabled(true);
                binding.disconnectButton.setEnabled(false);
                binding.clientIdEditText.setEnabled(true);
                break;
        }
    }

    private void loadClientId() {
        String clientId = sharedPreferences.getString(CLIENT_ID_KEY, "");
        binding.clientIdEditText.setText(clientId);
    }

    private void saveClientId() {
        SharedPreferences.Editor editor = sharedPreferences.edit();
        editor.putString(CLIENT_ID_KEY, binding.clientIdEditText.getText().toString());
        editor.apply();
    }

    private void showToast(String message) {
        Toast.makeText(this, message, Toast.LENGTH_SHORT).show();
    }

    private void checkAndRequestNotificationPermission() {
        if (ContextCompat.checkSelfPermission(this, android.Manifest.permission.POST_NOTIFICATIONS)
                != PackageManager.PERMISSION_GRANTED) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                requestNotificationPermission();
            }
        }
    }

    @RequiresApi(api = Build.VERSION_CODES.TIRAMISU)
    private void requestNotificationPermission() {
        ActivityResultLauncher<String[]> requestPermissionLauncher =
                registerForActivityResult(new ActivityResultContracts.RequestMultiplePermissions(),
                        permissions -> {
                            for (Map.Entry<String, Boolean> entry : permissions.entrySet()) {
                                if (entry.getKey().equals(android.Manifest.permission.POST_NOTIFICATIONS) && !entry.getValue()) {
                                    showToast("You won't see the VPN notification.");
                                }
                            }
                        });
        requestPermissionLauncher.launch(new String[]{android.Manifest.permission.POST_NOTIFICATIONS});
    }

    @Override
    public void onConnect() {
        Log.d(TAG, "Service Management bound. Core Version: " + sm.getVersion());
        if (!sm.isServiceDead() && sm.isLeafRunning()) {
            updateUi(UIState.CONNECTED);
        } else {
            updateUi(UIState.DISCONNECTED);
        }
    }

    @Override
    public void onDisconnect() {
        Log.d(TAG, "Service Management unbound/disconnected");
    }

    @Override
    public void onError(Throwable throwable) {
        Log.e(TAG, "Service Error", throwable);
        runOnUiThread(() -> showToast("Service Error: " + throwable.getMessage()));
    }
}