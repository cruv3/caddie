package com.llm_smartphone_v2.phone;

import android.accessibilityservice.AccessibilityService;
import android.accessibilityservice.GestureDescription;
import android.accessibilityservice.AccessibilityService.ScreenshotResult;
import android.content.Intent;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.Path;
import android.net.Uri;
import android.os.Build;
import android.provider.Settings;
import android.view.Display;
import android.view.accessibility.AccessibilityNodeInfo;

import com.llm_smartphone_v2.agent.TaskRequest;
import com.llm_smartphone_v2.accessibility.PhoneControlAccessibilityService;
import com.llm_smartphone_v2.util.JsonUtil;

import java.io.ByteArrayOutputStream;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

public class PhoneController {
    private final PhoneControlAccessibilityService service;

    public PhoneController(PhoneControlAccessibilityService service) {
        this.service = service;
    }

    public String currentScreenJson() {
        AccessibilityNodeInfo root = service.getRootInActiveWindow();
        try {
            return ScreenNodeSerializer.toJson(root);
        } finally {
            if (root != null) {
                root.recycle();
            }
        }
    }

    public byte[] takeScreenshotPng() {
        CompletableFuture<byte[]> future = new CompletableFuture<>();
        service.takeScreenshot(
                Display.DEFAULT_DISPLAY,
                service.getMainExecutor(),
                new AccessibilityService.TakeScreenshotCallback() {
                    @Override
                    public void onSuccess(ScreenshotResult screenshot) {
                        try {
                            future.complete(toPngBytes(screenshot));
                        } catch (Exception exception) {
                            future.completeExceptionally(exception);
                        }
                    }

                    @Override
                    public void onFailure(int errorCode) {
                        future.completeExceptionally(
                                new IllegalStateException("screenshot_failed_" + errorCode));
                    }
                }
        );
        try {
            return future.get(5, TimeUnit.SECONDS);
        } catch (Exception exception) {
            throw new IllegalStateException("screenshot_timeout_or_failure", exception);
        }
    }

    public String executeTask(TaskRequest request) {
        String task = request.task().toLowerCase(Locale.ROOT);
        if (task.contains("dark") || task.contains("dunkel")) {
            Intent intent = new Intent(Settings.ACTION_DISPLAY_SETTINGS);
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            service.startActivity(intent);
            return "{\"ok\":true,\"status\":\"opened_display_settings\"}";
        }
        return "{\"ok\":true,\"status\":\"received\",\"task\":\"" + JsonUtil.escape(request.task()) + "\"}";
    }

    public String tap(int x, int y) {
        return gesture(x, y, x, y, 80);
    }

    public String longPress(int x, int y, int durationMs) {
        return gesture(x, y, x, y, Math.max(300, durationMs));
    }

    public String swipe(int startX, int startY, int endX, int endY, int durationMs) {
        return gesture(startX, startY, endX, endY, Math.max(100, durationMs));
    }

    public String typeText(String text, boolean submit) {
        AccessibilityNodeInfo root = service.getRootInActiveWindow();
        AccessibilityNodeInfo focused = root == null ? null : root.findFocus(AccessibilityNodeInfo.FOCUS_INPUT);
        if (root != null) {
            root.recycle();
        }
        if (focused == null) {
            return JsonUtil.error("no_focused_input");
        }
        android.os.Bundle arguments = new android.os.Bundle();
        arguments.putCharSequence(
                AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE,
                text
        );
        boolean ok = focused.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT, arguments);
        if (ok && submit && Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            focused.performAction(AccessibilityNodeInfo.AccessibilityAction.ACTION_IME_ENTER.getId());
        }
        focused.recycle();
        return JsonUtil.ok(ok);
    }

    public String openApp(String packageName) {
        Intent intent = service.getPackageManager().getLaunchIntentForPackage(packageName);
        if (intent == null) {
            return JsonUtil.error("package_not_launchable");
        }
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        service.startActivity(intent);
        return JsonUtil.ok(true);
    }

    public String openUrl(String url) {
        Intent intent = new Intent(Intent.ACTION_VIEW, Uri.parse(url));
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        service.startActivity(intent);
        return JsonUtil.ok(true);
    }

    public String listApps() {
        PackageManager packageManager = service.getPackageManager();
        List<ApplicationInfo> apps = packageManager.getInstalledApplications(0);
        StringBuilder builder = new StringBuilder();
        builder.append("{\"packages\":[");
        int count = 0;
        for (ApplicationInfo app : apps) {
            Intent launchIntent = packageManager.getLaunchIntentForPackage(app.packageName);
            if (launchIntent == null) {
                continue;
            }
            if (count > 0) {
                builder.append(',');
            }
            builder.append('"').append(JsonUtil.escape(app.packageName)).append('"');
            count++;
        }
        builder.append("]}");
        return builder.toString();
    }

    public String globalAction(int action) {
        return JsonUtil.ok(service.performGlobalAction(action));
    }

    private String gesture(int startX, int startY, int endX, int endY, int durationMs) {
        Path path = new Path();
        path.moveTo(startX, startY);
        if (startX != endX || startY != endY) {
            path.lineTo(endX, endY);
        }
        GestureDescription gesture = new GestureDescription.Builder()
                .addStroke(new GestureDescription.StrokeDescription(path, 0, durationMs))
                .build();
        return JsonUtil.ok(service.dispatchGesture(gesture, null, null));
    }

    private byte[] toPngBytes(ScreenshotResult screenshot) {
        Bitmap bitmap = Bitmap.wrapHardwareBuffer(
                screenshot.getHardwareBuffer(),
                screenshot.getColorSpace()
        );
        try {
            if (bitmap == null) {
                throw new IllegalStateException("screenshot_bitmap_unavailable");
            }
            Bitmap softwareBitmap = bitmap.copy(Bitmap.Config.ARGB_8888, false);
            ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
            softwareBitmap.compress(Bitmap.CompressFormat.PNG, 100, outputStream);
            softwareBitmap.recycle();
            return outputStream.toByteArray();
        } finally {
            if (bitmap != null) {
                bitmap.recycle();
            }
            screenshot.getHardwareBuffer().close();
        }
    }
}
