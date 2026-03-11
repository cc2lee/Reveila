package com.reveila.android.safety;

import com.reveila.core.safety.ReveilaKillSwitch;
import com.reveila.core.safety.SafetyStatus;
import com.reveila.core.safety.AgentSafetyCommand;
import com.reveila.core.safety.SafetyAction;
import androidx.fragment.app.FragmentActivity;
import android.util.Log;
import java.nio.charset.StandardCharsets;

/**
 * Android-side implementation of the Sovereign Kill Switch.
 * Acts as a local watchdog, prioritizing reliability and efficiency.
 */
public class MobileKillSwitch implements ReveilaKillSwitch {

    public interface EmergencyStopListener {
        void onEmergencyStopAuthorized(AgentSafetyCommand command);
    }

    private final FragmentActivity activity;
    private final BiometricSafetyGuard biometricGuard;
    private final EmergencyStopListener listener;

    public MobileKillSwitch(FragmentActivity activity, EmergencyStopListener listener) {
        this.activity = activity;
        this.listener = listener;
        this.biometricGuard = new BiometricSafetyGuard(activity);
    }

    @Override
    public boolean isAuthorized(String agentId) {
        return true; 
    }

    @Override
    public void emergencyStopAll() {
        Log.i("MobileKillSwitch", "Emergency stop triggered. Launching biometric challenge...");
        
        // Data to sign (simplified for DTO representation)
        String dataToSign = "HALT_" + System.currentTimeMillis();
        
        biometricGuard.authenticateAndSign(
            dataToSign.getBytes(StandardCharsets.UTF_8),
            new BiometricSafetyGuard.BiometricCallback() {
                @Override
                public void onAuthenticationSucceeded(byte[] signature) {
                    Log.i("MobileKillSwitch", "Biometric authentication successful. Token signed.");
                    
                    AgentSafetyCommand command = new AgentSafetyCommand(
                        "GLOBAL", 
                        SafetyAction.HALT,
                        signature,
                        System.currentTimeMillis()
                    );
                    
                    if (listener != null) {
                        listener.onEmergencyStopAuthorized(command);
                    }
                }

                @Override
                public void onAuthenticationFailed(String error) {
                    Log.e("MobileKillSwitch", "Biometric authentication failed: " + error);
                }
            }
        );
    }

    @Override
    public SafetyStatus getStatus(String agentId) {
        return SafetyStatus.ACTIVE;
    }
}
