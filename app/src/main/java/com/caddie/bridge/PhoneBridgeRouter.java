package com.caddie.bridge;

import android.accessibilityservice.AccessibilityService;

import com.caddie.accessibility.AgentActivityTracker;
import com.caddie.accessibility.CompanionAccessibilityService;
import com.caddie.agent.TaskRequestParser;
import com.caddie.util.JsonUtil;

public class PhoneBridgeRouter {
    public HttpResponse route(HttpRequest request) {
        String body = request.body();
        if (request.startsWith("GET /health ")) {
            return HttpResponse.json("{\"ok\":true,\"accessibilityConnected\":"
                    + CompanionAccessibilityService.isConnected() + "}");
        }
        // Jeder echte Tool-Request (nicht /health) haelt den Run "aktiv" —
        // Grundlage dafuer, dass ein menschlicher Touch als Eingriff zaehlt.
        AgentActivityTracker.markRunActivity();
        if (request.startsWith("GET /screen ")) {
            return HttpResponse.json(CompanionAccessibilityService.currentScreenJson());
        }
        if (request.startsWith("GET /screenshot ")) {
            try {
                return HttpResponse.png(CompanionAccessibilityService.takeScreenshotPng());
            } catch (Exception exception) {
                return HttpResponse.json("{\"ok\":false,\"error\":\"screenshot_failed\",\"detail\":\""
                        + JsonUtil.escape(exception.toString()) + "\"}");
            }
        }
        if (request.startsWith("GET /apps ")) {
            return HttpResponse.json(CompanionAccessibilityService.listApps());
        }
        if (request.startsWith("POST /task ")) {
            return HttpResponse.json(CompanionAccessibilityService.executeTask(TaskRequestParser.parse(body)));
        }
        if (request.startsWith("POST /tap ")) {
            return HttpResponse.json(CompanionAccessibilityService.tap(
                    JsonBody.intValue(body, "x"),
                    JsonBody.intValue(body, "y")
            ));
        }
        if (request.startsWith("POST /long_press ")) {
            return HttpResponse.json(CompanionAccessibilityService.longPress(
                    JsonBody.intValue(body, "x"),
                    JsonBody.intValue(body, "y"),
                    JsonBody.intValue(body, "durationMs", 700)
            ));
        }
        if (request.startsWith("POST /swipe ")) {
            return HttpResponse.json(CompanionAccessibilityService.swipe(
                    JsonBody.intValue(body, "startX"),
                    JsonBody.intValue(body, "startY"),
                    JsonBody.intValue(body, "endX"),
                    JsonBody.intValue(body, "endY"),
                    JsonBody.intValue(body, "durationMs", 300)
            ));
        }
        if (request.startsWith("POST /type_text ")) {
            return HttpResponse.json(CompanionAccessibilityService.typeText(
                    JsonBody.stringValue(body, "text"),
                    JsonBody.booleanValue(body, "submit")
            ));
        }
        if (request.startsWith("POST /open_app ")) {
            return HttpResponse.json(CompanionAccessibilityService.openApp(
                    JsonBody.stringValue(body, "packageName")
            ));
        }
        if (request.startsWith("POST /open_url ")) {
            return HttpResponse.json(CompanionAccessibilityService.openUrl(
                    JsonBody.stringValue(body, "url")
            ));
        }
        if (request.startsWith("POST /back ")) {
            return HttpResponse.json(CompanionAccessibilityService.globalAction(
                    AccessibilityService.GLOBAL_ACTION_BACK
            ));
        }
        if (request.startsWith("POST /home ")) {
            return HttpResponse.json(CompanionAccessibilityService.globalAction(
                    AccessibilityService.GLOBAL_ACTION_HOME
            ));
        }
        return HttpResponse.json("{\"ok\":false,\"error\":\"not_found\"}");
    }
}
