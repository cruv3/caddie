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

    public static String uninstallApp(String packageName) {
        PhoneController controller = controllerOrNull();
        return controller == null ? JsonUtil.error("accessibility_service_not_connected") : controller.uninstallApp(packageName);
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
        // Diagnose: jedes ankommende Event loggen, damit wir sehen welche
        // Typen ueberhaupt durchkommen. Wenn pausieren nicht feuert liegt's
        // entweder an fehlender Subscription oder an einem Filter weiter unten.
        android.util.Log.v("CompanionA11y", "event arrived type=" + type
                + " (" + AccessibilityEvent.eventTypeToString(type) + ")"
                + " pkg=" + event.getPackageName());
        boolean interactive =
                type == AccessibilityEvent.TYPE_VIEW_CLICKED
                || type == AccessibilityEvent.TYPE_VIEW_LONG_CLICKED
                || type == AccessibilityEvent.TYPE_TOUCH_INTERACTION_START
                || type == AccessibilityEvent.TYPE_VIEW_SCROLLED;
        if (!interactive) {
            return;
        }
        // Knopflose Intervention-Erkennung: Interaktionen waehrend eines
        // laufenden Runs sind menschliche Eingriffe -> Agent pausieren.
        // TYPE_TOUCH_INTERACTION_START feuert NICHT bei dispatchGesture()
        // (sofern es ueberhaupt feuert — braucht teils Touch-Exploration).
        // TYPE_VIEW_SCROLLED feuert leider auch beim Settling nach
        // Agent-Swipes — daher Filter per isAgentActive(): Scroll-Events
        // innerhalb des Agent-Aktiv-Fensters ignorieren.
        boolean runActive = AgentActivityTracker.isRunLikelyActive();
        boolean agentActive = AgentActivityTracker.isAgentActive();
        // HTTP-bridge backend: the agent acts via dispatchGesture, which fires
        // NO accessibility events. So a click / long-click / touch-start during
        // a run is ALWAYS the human -> pause immediately (no agent-active guess).
        // Scrolls still fire from the agent's own list-settling after a swipe,
        // so keep the agent-active guard for scroll events only.
        boolean isTap = type == AccessibilityEvent.TYPE_VIEW_CLICKED
                || type == AccessibilityEvent.TYPE_VIEW_LONG_CLICKED
                || type == AccessibilityEvent.TYPE_TOUCH_INTERACTION_START;
        boolean isScroll = type == AccessibilityEvent.TYPE_VIEW_SCROLLED;
        android.util.Log.i("CompanionA11y", "interactive event type=" + type
                + " (" + AccessibilityEvent.eventTypeToString(type) + ")"
                + " runActive=" + runActive + " agentActive=" + agentActive
                + " isTap=" + isTap + " isScroll=" + isScroll);
        // Human-takeover detection moved to the PC-side getevent watcher (ADB
        // backend): it reads /dev/input directly -> faster (kernel real-time,
        // in-process pause, no HTTP hop) and catches gestures a11y misses
        // (e.g. web scrolls = only WINDOW_CONTENT_CHANGED). Flip this back to
        // true to use the old a11y path (e.g. with the HTTP-bridge backend).
        final boolean touchPauseViaA11y = false;
        if (touchPauseViaA11y && runActive && (isTap || (isScroll && !agentActive))) {
            InterventionReporter.get().onHumanInteraction();
            com.caddie.overlay.OverlayService.Companion.notifyPaused(this);
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
