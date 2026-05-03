package com.llm_smartphone_v2.phone;

import android.graphics.Rect;
import android.view.accessibility.AccessibilityNodeInfo;

import com.llm_smartphone_v2.util.JsonUtil;

import java.util.ArrayList;
import java.util.List;

/**
 * Dumb-pipe serializer: emits every accessibility node field that might be useful
 * to the MCP server. The server side is responsible for filtering, compacting, and
 * decision-making — this class does no transformation.
 */
public final class ScreenNodeSerializer {
    private static final int MAX_NODES = 200;

    private ScreenNodeSerializer() {
    }

    public static String toJson(AccessibilityNodeInfo root) {
        if (root == null) {
            return "{\"connected\":true,\"nodes\":[]}";
        }
        StringBuilder builder = new StringBuilder();
        builder.append("{\"connected\":true,\"nodes\":[");
        appendNode(builder, root, 0, new int[]{0});
        builder.append("]}");
        return builder.toString();
    }

    private static void appendNode(StringBuilder builder, AccessibilityNodeInfo node, int depth, int[] count) {
        if (node == null || count[0] >= MAX_NODES) {
            return;
        }
        if (count[0] > 0) {
            builder.append(',');
        }
        NodeSnapshot snapshot = snapshotOf(node, count[0], depth);
        count[0]++;
        appendSnapshot(builder, snapshot);

        for (int i = 0; i < node.getChildCount(); i++) {
            AccessibilityNodeInfo child = node.getChild(i);
            appendNode(builder, child, depth + 1, count);
            if (child != null) {
                child.recycle();
            }
        }
    }

    private static NodeSnapshot snapshotOf(AccessibilityNodeInfo node, int id, int depth) {
        Rect bounds = new Rect();
        node.getBoundsInScreen(bounds);

        NodeSnapshot snapshot = new NodeSnapshot();
        snapshot.id = id;
        snapshot.depth = depth;
        snapshot.text = node.getText();
        snapshot.description = node.getContentDescription();
        snapshot.className = node.getClassName();
        snapshot.resourceId = node.getViewIdResourceName();
        snapshot.clickable = node.isClickable();
        snapshot.checkable = node.isCheckable();
        snapshot.checked = node.isChecked();
        snapshot.enabled = node.isEnabled();
        snapshot.selected = node.isSelected();
        snapshot.focused = node.isFocused();
        snapshot.scrollable = node.isScrollable();
        snapshot.password = node.isPassword();
        snapshot.editable = node.isEditable();
        snapshot.boundsLeft = bounds.left;
        snapshot.boundsTop = bounds.top;
        snapshot.boundsRight = bounds.right;
        snapshot.boundsBottom = bounds.bottom;

        AccessibilityNodeInfo.RangeInfo range = node.getRangeInfo();
        if (range != null) {
            snapshot.rangeType = range.getType();
            snapshot.rangeMin = range.getMin();
            snapshot.rangeMax = range.getMax();
            snapshot.rangeCurrent = range.getCurrent();
            snapshot.hasRange = true;
        }

        for (AccessibilityNodeInfo.AccessibilityAction action : node.getActionList()) {
            snapshot.actions.add(action.getId());
        }

        return snapshot;
    }

    /**
     * Pure JSON serialization. Exposed for unit tests — production code goes through
     * {@link #toJson(AccessibilityNodeInfo)} which feeds AccessibilityNodeInfo into a
     * NodeSnapshot first.
     */
    public static void appendSnapshot(StringBuilder builder, NodeSnapshot s) {
        builder.append('{')
                .append("\"id\":").append(s.id)
                .append(",\"depth\":").append(s.depth)
                .append(",\"text\":\"").append(JsonUtil.escape(s.text)).append('"')
                .append(",\"description\":\"").append(JsonUtil.escape(s.description)).append('"')
                .append(",\"className\":\"").append(JsonUtil.escape(s.className)).append('"')
                .append(",\"resourceId\":\"").append(JsonUtil.escape(s.resourceId)).append('"')
                .append(",\"clickable\":").append(s.clickable)
                .append(",\"checkable\":").append(s.checkable)
                .append(",\"checked\":").append(s.checked)
                .append(",\"enabled\":").append(s.enabled)
                .append(",\"selected\":").append(s.selected)
                .append(",\"focused\":").append(s.focused)
                .append(",\"scrollable\":").append(s.scrollable)
                .append(",\"password\":").append(s.password)
                .append(",\"editable\":").append(s.editable)
                .append(",\"bounds\":{\"left\":").append(s.boundsLeft)
                .append(",\"top\":").append(s.boundsTop)
                .append(",\"right\":").append(s.boundsRight)
                .append(",\"bottom\":").append(s.boundsBottom)
                .append('}');

        if (s.hasRange) {
            builder.append(",\"rangeInfo\":{")
                    .append("\"type\":").append(s.rangeType)
                    .append(",\"min\":").append(formatFloat(s.rangeMin))
                    .append(",\"max\":").append(formatFloat(s.rangeMax))
                    .append(",\"current\":").append(formatFloat(s.rangeCurrent))
                    .append('}');
        }

        builder.append(",\"actions\":[");
        for (int i = 0; i < s.actions.size(); i++) {
            if (i > 0) {
                builder.append(',');
            }
            builder.append(s.actions.get(i));
        }
        builder.append(']')
                .append('}');
    }

    private static String formatFloat(float value) {
        if (Float.isNaN(value) || Float.isInfinite(value)) {
            return "0";
        }
        if (value == (long) value) {
            return Long.toString((long) value);
        }
        return Float.toString(value);
    }

    /** Plain value object representing one node. Mutable on purpose so tests can populate it directly. */
    public static final class NodeSnapshot {
        public int id;
        public int depth;
        public CharSequence text;
        public CharSequence description;
        public CharSequence className;
        public CharSequence resourceId;
        public boolean clickable;
        public boolean checkable;
        public boolean checked;
        public boolean enabled;
        public boolean selected;
        public boolean focused;
        public boolean scrollable;
        public boolean password;
        public boolean editable;
        public int boundsLeft;
        public int boundsTop;
        public int boundsRight;
        public int boundsBottom;
        public boolean hasRange;
        public int rangeType;
        public float rangeMin;
        public float rangeMax;
        public float rangeCurrent;
        public final List<Integer> actions = new ArrayList<>();
    }
}
