package com.caddie.phone;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import com.caddie.phone.ScreenNodeSerializer.NodeSnapshot;

import org.junit.Test;

public class ScreenNodeSerializerTest {

    @Test
    public void emitsAllStandardFields() {
        NodeSnapshot s = baseSnapshot();
        s.text = "Helligkeit";
        s.description = "Slider";
        s.className = "android.widget.SeekBar";
        s.resourceId = "com.android.settings:id/brightness_slider";
        s.clickable = true;
        s.enabled = true;
        s.boundsLeft = 63;
        s.boundsTop = 205;
        s.boundsRight = 1017;
        s.boundsBottom = 331;

        StringBuilder out = new StringBuilder();
        ScreenNodeSerializer.appendSnapshot(out, s);
        String json = out.toString();

        assertContains(json, "\"text\":\"Helligkeit\"");
        assertContains(json, "\"description\":\"Slider\"");
        assertContains(json, "\"className\":\"android.widget.SeekBar\"");
        assertContains(json, "\"resourceId\":\"com.android.settings:id/brightness_slider\"");
        assertContains(json, "\"clickable\":true");
        assertContains(json, "\"enabled\":true");
        assertContains(json,
                "\"bounds\":{\"left\":63,\"top\":205,\"right\":1017,\"bottom\":331}");
        assertContains(json, "\"actions\":[]");
        assertFalse("range should be absent when hasRange=false", json.contains("\"rangeInfo\""));
    }

    @Test
    public void emitsRangeInfoWhenPresent() {
        NodeSnapshot s = baseSnapshot();
        s.className = "android.widget.SeekBar";
        s.hasRange = true;
        s.rangeType = 0; // INT
        s.rangeMin = 0f;
        s.rangeMax = 255f;
        s.rangeCurrent = 128f;

        StringBuilder out = new StringBuilder();
        ScreenNodeSerializer.appendSnapshot(out, s);
        String json = out.toString();

        assertContains(json,
                "\"rangeInfo\":{\"type\":0,\"min\":0,\"max\":255,\"current\":128}");
    }

    @Test
    public void emitsRangeInfoWithFractionalCurrent() {
        NodeSnapshot s = baseSnapshot();
        s.hasRange = true;
        s.rangeType = 1;
        s.rangeMin = 0f;
        s.rangeMax = 1f;
        s.rangeCurrent = 0.5f;

        StringBuilder out = new StringBuilder();
        ScreenNodeSerializer.appendSnapshot(out, s);

        assertContains(out.toString(), "\"current\":0.5");
    }

    @Test
    public void emitsActionsArray() {
        NodeSnapshot s = baseSnapshot();
        s.actions.add(16);       // ACTION_CLICK
        s.actions.add(0x800020); // ACTION_SET_PROGRESS

        StringBuilder out = new StringBuilder();
        ScreenNodeSerializer.appendSnapshot(out, s);
        String json = out.toString();

        assertContains(json, "\"actions\":[16,8388640]");
    }

    @Test
    public void escapesQuotesAndBackslashesInStrings() {
        NodeSnapshot s = baseSnapshot();
        s.text = "Hello \"World\"";
        s.description = "back\\slash";

        StringBuilder out = new StringBuilder();
        ScreenNodeSerializer.appendSnapshot(out, s);
        String json = out.toString();

        assertContains(json, "\"text\":\"Hello \\\"World\\\"\"");
        assertContains(json, "\"description\":\"back\\\\slash\"");
    }

    @Test
    public void nullStringsBecomeEmpty() {
        NodeSnapshot s = baseSnapshot();
        s.text = null;
        s.description = null;
        s.className = null;
        s.resourceId = null;

        StringBuilder out = new StringBuilder();
        ScreenNodeSerializer.appendSnapshot(out, s);
        String json = out.toString();

        assertContains(json, "\"text\":\"\"");
        assertContains(json, "\"description\":\"\"");
        assertContains(json, "\"className\":\"\"");
        assertContains(json, "\"resourceId\":\"\"");
    }

    @Test
    public void emitsAllStateFlags() {
        NodeSnapshot s = baseSnapshot();
        s.checkable = true;
        s.checked = true;
        s.selected = true;
        s.focused = true;
        s.scrollable = true;
        s.password = true;
        s.editable = true;

        StringBuilder out = new StringBuilder();
        ScreenNodeSerializer.appendSnapshot(out, s);
        String json = out.toString();

        assertContains(json, "\"checkable\":true");
        assertContains(json, "\"checked\":true");
        assertContains(json, "\"selected\":true");
        assertContains(json, "\"focused\":true");
        assertContains(json, "\"scrollable\":true");
        assertContains(json, "\"password\":true");
        assertContains(json, "\"editable\":true");
    }

    private static NodeSnapshot baseSnapshot() {
        NodeSnapshot s = new NodeSnapshot();
        s.id = 0;
        s.depth = 0;
        return s;
    }

    private static void assertContains(String haystack, String needle) {
        assertTrue("expected to find '" + needle + "' in:\n" + haystack,
                haystack.contains(needle));
    }
}
