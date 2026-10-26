//Aalvee
package edu.sentencebuilder.ui;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import org.junit.jupiter.api.Test;

class BatchGenerateControllerTest {
    @Test
    void acceptsCountsWithinTheSupportedRange() {
        assertEquals(1, BatchGenerateController.parseCount("1"));
        assertEquals(10, BatchGenerateController.parseCount(" 10 "));
        assertEquals(1000, BatchGenerateController.parseCount("1000"));
    }

    @Test
    void rejectsInvalidOrOutOfRangeCounts() {
        for (String input : new String[]{"", " ", "abc", "1.5", "0", "-1", "1001", "999999999999"}) {
            assertNull(BatchGenerateController.parseCount(input), input);
        }
        assertNull(BatchGenerateController.parseCount(null));
    }
}
