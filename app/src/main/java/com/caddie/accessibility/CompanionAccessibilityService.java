package com.caddie.accessibility;

import android.accessibilityservice.AccessibilityService;
import android.view.accessibility.AccessibilityEvent;

import com.caddie.agent.TaskRequest;
import com.caddie.phone.PhoneController;
import com.caddie.util.JsonUtil;

public class CompanionAccessibilityService extends AccessibilityService {
    private static volatile CompanionAccessibilityService instance;
    private PhoneController controller;

    public static boolean isConnected() {
        return instance != null;
    }

    public static CompanionAccessibilityService getInstance() {
        return instance;
    }

    public static String currentScreenJson() {
        PhoneController controller = controllerOrNull();
        return controller == null ? "{\"connected\":false,\"nodes\":[]}" : controller.currentScreenJson();
    }

    public static byte[] takeScreenshotPng() {
        PhoneController controller = controllerOrNull();
        if (controller == null) {
            throw new IllegalStateException("accessibility_service_not_connected");
        }
        return controller.takeScreenshotPng();
    }

    public static String executeTask(TaskRequest request) {
        PhoneController controller = controllerOrNull();
        return controller == null ? JsonUtil.error("accessibility_service_not_connected") : controller.executeTask(request);
    }

    public static String tap(int x, int y) {
        PhoneController controller = controllerOrNull();
        return controller == null ? JsonUtil.error("accessibility_service_not_connected") : controller.tap(x, y);
    }

    public static String longPress(int x, int y, int durationMs) {
        PhoneController controller = controllerOrNull();
        return controller == null ? JsonUtil.error("accessibility_service_not_connected") : controller.longPress(x, y, durationMs);
    }

    public static String swipe(int startX, int startY, int endX, int endY, int durationMs) {
        PhoneController controller = controllerOrNull();
        return controller == null ? JsonUtil.error("accessibility_service_not_connected") : controller.swipe(startX, startY, endX, endY, durationMs);
    }

    public static String typeText(String text, boolean submit) {
        PhoneController controller = controllerOrNull();
        return controller == null ? JsonUtil.error("accessibility_service_not_connected") : controller.typeText(text, submit);
    }

    public static String openApp(String packageName) {
        PhoneController controller = controllerOrNull();
        return controller == null ? JsonUtil.error("accessibility_service_not_connected") : controller.openApp(packageName);
    }

    public static String openUrl(String url) {
        PhoneController controller = controllerOrNull();
        return controller == null ? JsonUtil.error("accessibility_service_not_connected") : controller.openUrl(url);
    }

    public static String listApps() {
        PhoneController controller = controllerOrNull();
        return controller == null ? "{\"packages\":[]}" : controller.listApps();
    }

    public static String globalAction(int action) {
        PhoneController controller = controllerOrNull();
        return controller == null ? JsonUtil.error("accessibility_service_not_connected") : controller.globalAction(action);
    }

    @Override
    protected void onServiceConnected() {
        android.util.Log.i("CompanionA11y", "onServiceConnected (instance=" + System.identityHashCode(this) + ")");
        controller = new PhoneController(this);
        instance = this;
        CompanionServiceLauncher.startAll(this);
        // We used to also call OverlayService.notifyAccessibilityConnected(this)
        // here so the overlay could re-mount as TYPE_ACCESSIBILITY_OVERLAY (the
        // accessibility-overlay window type that survives Settings's
        // setHideOverlayWindows(true)). That coupling backfired badly on
        // Android 14+: the AS rebinds aggressively whenever the user enters
        // Settings sub-screens, and each rebind would tear our overlay window
        // down and rebuild it, producing a visible pop-in/pop-out flicker.
        // The overlay now lives independently as a plain
        // TYPE_APPLICATION_OVERLAY and is invisible only inside Settings —
        // a smaller cosmetic gap, no flicker, much simpler code.
        if (AccessibilityReturnFlag.consume(this)) {
            CompanionServiceLauncher.bringMainActivityForward(this);
        }
    }

    @Override
    public void onAccessibilityEvent(AccessibilityEvent event) {
        if (event == null) {
            return;
        }
        int type = event.getEventType();
        boolean interactive =
                type == AccessibilityEvent.TYPE_VIEW_CLICKED
                || type == AccessibilityEvent.TYPE_VIEW_LONG_CLICKED
                || type == AccessibilityEvent.TYPE_TOUCH_INTERACTION_START;
        if (!interactive) {
            return;
        }
        // Knopflose Intervention-Erkennung: jede Interaktion waehrend eines
        // laufenden Runs ist ein menschlicher Eingriff -> Agent pausieren.
        // TYPE_TOUCH_INTERACTION_START feuert NICHT bei dispatchGesture(),
        // also kein Self-Pause bei Agent-Swipes. Fuer TYPE_VIEW_CLICKED /
        // _LONG_CLICKED filtern wir weiterhin per isAgentActive(), weil der
        // Agent diese Events durch eigene Gesten ausloesen kann.
        boolean runActive = AgentActivityTracker.isRunLikelyActive();
        boolean agentActive = AgentActivityTracker.isAgentActive();
        boolean fromTouchStart = type == AccessibilityEvent.TYPE_TOUCH_INTERACTION_START;
        android.util.Log.i("CompanionA11y", "interactive event type=" + type
                + " runActive=" + runActive + " agentActive=" + agentActive
                + " fromTouchStart=" + fromTouchStart);
        if (runActive && (fromTouchStart || !agentActive)) {
            InterventionReporter.get().onHumanInteraction();
        }
    }

    @Override
    public boolean onUnbind(android.content.Intent intent) {
        android.util.Log.w("CompanionA11y", "onUnbind (instance=" + System.identityHashCode(this) + ")");
        return super.onUnbind(intent);
    }

    @Override
    public void onInterrupt() {
        android.util.Log.w("CompanionA11y", "onInterrupt (instance=" + System.identityHashCode(this) + ")");
    }

    @Override
    public void onDestroy() {
        android.util.Log.w("CompanionA11y", "onDestroy (instance=" + System.identityHashCode(this) + ")");
        if (instance == this) {
            instance = null;
        }
        super.onDestroy();
    }

    private static PhoneController controllerOrNull() {
        CompanionAccessibilityService service = instance;
        return service == null ? null : service.controller;
    }
}
