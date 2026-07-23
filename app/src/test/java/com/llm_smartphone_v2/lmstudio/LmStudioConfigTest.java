package com.caddie.lmstudio;

import static org.junit.Assert.assertEquals;

import org.junit.Test;

public class LmStudioConfigTest {
    @Test
    public void studyBuildUsesAdbReverseEndpoint() {
        assertEquals("http://127.0.0.1:8787", LmStudioConfig.BASE_URL);
    }
}
