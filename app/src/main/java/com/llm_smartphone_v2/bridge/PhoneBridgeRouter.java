package com.llm_smartphone_v2.bridge;

import android.accessibilityservice.AccessibilityService;

import com.llm_smartphone_v2.accessibility.PhoneControlAccessibilityService;
import com.llm_smartphone_v2.agent.TaskRequestParser;
import com.llm_smartphone_v2.util.JsonUtil;

public class PhoneBridgeRouter {
    public HttpResponse route(HttpRequest request) {
        String body = request.body();
        if (request.startsWith("GET /health ")) {
            return HttpResponse.json("{\"ok\":true,\"accessibilityConnected\":"
                    + PhoneControlAccessibilityService.isConnected() + "}");
        }
        if (request.startsWith("GET /screen ")) {
            return HttpResponse.json(PhoneControlAccessibilityService.currentScreenJson());
        }
        if (request.startsWith("GET /screenshot ")) {
            try {
                return HttpResponse.png(PhoneControlAccessibilityService.takeScreenshotPng());
            } catch (Exception exception) {
                return HttpResponse.json("{\"ok\":false,\"error\":\"screenshot_failed\",\"detail\":\""
                        + JsonUtil.escape(exception.toString()) + "\"}");
            }
        }
        if (request.startsWith("GET /apps ")) {
            return HttpResponse.json(PhoneControlAccessibilityService.listApps());
        }
        if (request.startsWith("POST /task ")) {
            return HttpResponse.json(PhoneControlAccessibilityService.executeTask(TaskRequestParser.parse(body)));
        }
        if (request.startsWith("POST /tap ")) {
            return HttpResponse.json(PhoneControlAccessibilityService.tap(
                    JsonBody.intValue(body, "x"),
                    JsonBody.intValue(body, "y")
            ));
        }
        if (request.startsWith("POST /long_press ")) {
            return HttpResponse.json(PhoneControlAccessibilityService.longPress(
                    JsonBody.intValue(body, "x"),
                    JsonBody.intValue(body, "y"),
                    JsonBody.intValue(body, "durationMs", 700)
            ));
        }
        if (request.startsWith("POST /swipe ")) {
            return HttpResponse.json(PhoneControlAccessibilityService.swipe(
                    JsonBody.intValue(body, "startX"),
                    JsonBody.intValue(body, "startY"),
                    JsonBody.intValue(body, "endX"),
                    JsonBody.intValue(body, "endY"),
                    JsonBody.intValue(body, "durationMs", 300)
            ));
        }
        if (request.startsWith("POST /type_text ")) {
            return HttpResponse.json(PhoneControlAccessibilityService.typeText(
                    JsonBody.stringValue(body, "text"),
                    JsonBody.booleanValue(body, "submit")
            ));
        }
        if (request.startsWith("POST /open_app ")) {
            return HttpResponse.json(PhoneControlAccessibilityService.openApp(
                    JsonBody.stringValue(body, "packageName")
            ));
        }
        if (request.startsWith("POST /open_url ")) {
            return HttpResponse.json(PhoneControlAccessibilityService.openUrl(
                    JsonBody.stringValue(body, "url")
            ));
        }
        if (request.startsWith("POST /back ")) {
            return HttpResponse.json(PhoneControlAccessibilityService.globalAction(
                    AccessibilityService.GLOBAL_ACTION_BACK
            ));
        }
        if (request.startsWith("POST /home ")) {
            return HttpResponse.json(PhoneControlAccessibilityService.globalAction(
                    AccessibilityService.GLOBAL_ACTION_HOME
            ));
        }
        return HttpResponse.json("{\"ok\":false,\"error\":\"not_found\"}");
    }
}
