package com.llm_smartphone_v2.accessibility;

import android.accessibilityservice.AccessibilityService;
import android.view.accessibility.AccessibilityEvent;

import com.llm_smartphone_v2.agent.TaskRequest;
import com.llm_smartphone_v2.phone.PhoneController;
import com.llm_smartphone_v2.util.JsonUtil;

public class PhoneControlAccessibilityService extends AccessibilityService {
    private static volatile PhoneControlAccessibilityService instance;
    private PhoneController controller;

    public static boolean isConnected() {
        return instance != null;
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
        controller = new PhoneController(this);
        instance = this;
    }

    @Override
    public void onAccessibilityEvent(AccessibilityEvent event) {
    }

    @Override
    public void onInterrupt() {
    }

    @Override
    public void onDestroy() {
        if (instance == this) {
            instance = null;
        }
        super.onDestroy();
    }

    private static PhoneController controllerOrNull() {
        PhoneControlAccessibilityService service = instance;
        return service == null ? null : service.controller;
    }
}
