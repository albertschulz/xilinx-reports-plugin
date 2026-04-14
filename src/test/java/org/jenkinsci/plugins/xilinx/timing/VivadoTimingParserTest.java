package org.jenkinsci.plugins.xilinx.timing;

import net.praqma.jenkins.memorymap.result.MemoryMapConfigMemory;
import net.praqma.jenkins.memorymap.result.MemoryMapConfigMemoryItem;
import org.junit.Before;
import org.junit.Test;

import java.io.File;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

public class VivadoTimingParserTest {

    private String filename;
    private VivadoTimingParser dut;

    @Before
    public void setUp() {
        dut = new VivadoTimingParser();
        filename = VivadoTimingParserTest.class.getResource("timing_summary.rpt").getFile();
    }

    @Test
    public void getResources() throws IOException {
        File f = new File(filename);
        MemoryMapConfigMemory configMemory = dut.getResources(f);

        // The Design Timing Summary in the fixture has WNS=0.296ns, WHS=0.012ns.
        // The parser converts ns -> ps and stores hex strings.
        Map<String, Integer> expectedPs = new HashMap<>();
        expectedPs.put("WNS_ps", 296);
        expectedPs.put("WHS_ps", 12);

        assertEquals("Expected exactly 2 timing items (WNS_ps, WHS_ps)", 2, configMemory.size());

        for (MemoryMapConfigMemoryItem item : configMemory) {
            Integer expected = expectedPs.get(item.getName());
            assertNotNull("Unexpected item name: " + item.getName(), expected);
            int actual = Integer.parseInt(item.getUsed().replace("0x", ""), 16);
            assertEquals(item.getName() + " ps mismatch", expected.intValue(), actual);

            // Length must be the slack ceiling so Util% remains in [0, 100].
            int length = Integer.parseInt(item.getLength().replace("0x", ""), 16);
            assertEquals("Length must equal SLACK_CEILING_PS", VivadoTimingParser.SLACK_CEILING_PS, length);

            int unused = Integer.parseInt(item.getUnused().replace("0x", ""), 16);
            assertEquals("used + unused must equal length", length, actual + unused);
        }
    }

    @Test
    public void anchorsOnDesignTimingSummaryNotIntraClockTable() throws IOException {
        // The fixture has an Intra Clock Table later on with the same column headers
        // and a row whose first numeric is also 0.296 (clk125). The parser must lock
        // onto the Design Timing Summary block and ignore the later table.
        File f = new File(filename);
        MemoryMapConfigMemory configMemory = dut.getResources(f);
        assertTrue(configMemory.size() > 0);
        // If we accidentally matched the Intra Clock Table, we'd see more than 2 items
        // (or different values). The strict size check above already covers this, but
        // keep this test to make the intent explicit for future maintainers.
        assertEquals(2, configMemory.size());
    }
}
