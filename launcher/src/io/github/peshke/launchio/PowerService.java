package io.github.peshke.launchio;

import android.accessibilityservice.AccessibilityService;
import android.content.Intent;
import android.view.accessibility.AccessibilityEvent;

/**
 * Restart and standby for the Settings menu. Apps can't reboot or sleep the TV themselves, but an
 * accessibility service can open the system power dialog or put the screen to sleep. It reads no
 * screen content and handles no events.
 * Enable over ADB: settings put secure enabled_accessibility_services io.github.peshke.launchio/.PowerService
 */
public class PowerService extends AccessibilityService {
    static PowerService running;

    @Override protected void onServiceConnected() { running = this; }

    @Override public boolean onUnbind(Intent intent) {
        running = null;
        return super.onUnbind(intent);
    }

    @Override public void onAccessibilityEvent(AccessibilityEvent event) { }

    @Override public void onInterrupt() { }
}
