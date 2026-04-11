package com.reveila.system;

import org.junit.jupiter.api.Test;
import java.io.IOException;

public class PerimeterEnforcementMergerTest {

    @Test
    public void testMerger() throws IOException {
        PerimeterEnforcementMerger merger = new PerimeterEnforcementMerger();
        merger.mergePerimeterSettings();
    }
}
