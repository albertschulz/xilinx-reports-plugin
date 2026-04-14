package org.jenkinsci.plugins.xilinx.utilization;

import net.praqma.jenkins.memorymap.result.MemoryMapConfigMemory;
import net.praqma.jenkins.memorymap.result.MemoryMapConfigMemoryItem;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import java.io.File;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public class VivadoUtilizationParserTest {

    private String filename;
    private VivadoUtilizationParser dut;

    @Before
    public void setUp() throws Exception {
        dut = new VivadoUtilizationParser();
        filename = VivadoUtilizationParserTest.class.getResource("utilization.rpt").getFile();
    }

    @After
    public void tearDown() throws Exception {

    }

    private static void ItemFactory(String name, int used, int available)
    {
        String safe_name = name.trim().replace(" ", "_");
        int unused = available - used;
        //String length, String used, String unused
        myMap.put(safe_name, new MemoryMapConfigMemoryItem(safe_name, "0",
                Integer.toString(available),
                Integer.toString(used),
                Integer.toString(unused)));
    }
    private static Map<String, MemoryMapConfigMemoryItem> myMap;
    static {
        myMap = new HashMap<>();
        ItemFactory("Slice LUTs           " ,10122, 17600);
        ItemFactory("LUT as Logic         " , 9472, 17600);
        ItemFactory("LUT as Memory        " ,  650,  6000);
        ItemFactory("Slice Registers      " ,11935, 35200);
        ItemFactory("Register as Flip Flop" ,11933, 35200);
        ItemFactory("Register as Latch    " ,    2, 35200);
        ItemFactory("Slice                " , 3718,  4400);
        ItemFactory("LUT as Logic         " , 9472, 17600);
        ItemFactory("LUT as Memory        " ,  650,  6000);
        ItemFactory("LUT Flip Flop Pairs  " , 4930, 17600);
        ItemFactory("Block RAM Tile       " ,   47,    60);
        ItemFactory("RAMB36/FIFO          " ,   45,    60);
        ItemFactory("RAMB18               " ,    5,   120);
        ItemFactory("DSPs                 " ,   10,    80);
    }

    @Test
    public void getResources() throws IOException{
        File f = new File(filename);

        MemoryMapConfigMemory configMemory;
        //dut.parseConfigFile(f) returns the same thing.
        //configMemory = dut.parseMapFile(f, null);
        configMemory = dut.getResources(f);
        assertTrue(configMemory.size() > 0);

        int assertions = 0;
        for (MemoryMapConfigMemoryItem item : configMemory) {
            MemoryMapConfigMemoryItem golden_item = myMap.get(item.getName());
            if (golden_item != null) {
                assertEquals(item.getName(), Integer.getInteger(golden_item.getLength()), Integer.getInteger(item.getLength()));
                assertEquals(item.getName(), Integer.getInteger(golden_item.getUsed()), Integer.getInteger(item.getUsed()));
                assertEquals(item.getName(), Integer.getInteger(golden_item.getUnused()), Integer.getInteger(item.getUnused()));
                assertions++;
            }
        }
        assertEquals(assertions, myMap.size());
    }

    /**
     * Vivado 2020+ adds a "Prohibited" column to the utilization tables, making them
     * 6 columns wide instead of 5. Verify the parser handles the modern format.
     */
    @Test
    public void getResourcesVivado2022() throws IOException {
        File f = new File(VivadoUtilizationParserTest.class.getResource("utilization_v2022.rpt").getFile());
        MemoryMapConfigMemory configMemory = dut.getResources(f);

        Map<String, int[]> expected = new HashMap<>();
        // name -> {used, available}
        expected.put("CLB_LUTs",                new int[]{1748, 117120});
        expected.put("LUT_as_Logic",            new int[]{1723, 117120});
        expected.put("LUT_as_Memory",           new int[]{25,    57600});
        expected.put("CLB_Registers",           new int[]{5127, 234240});
        expected.put("Register_as_Flip_Flop",   new int[]{5127, 234240});
        expected.put("Block_RAM_Tile",          new int[]{22,      144});
        expected.put("RAMB36/FIFO",             new int[]{22,      144});
        expected.put("RAMB18",                  new int[]{0,       288});
        expected.put("URAM",                    new int[]{0,        64});
        expected.put("DSPs",                    new int[]{0,      1248});

        int seen = 0;
        for (MemoryMapConfigMemoryItem item : configMemory) {
            int[] golden = expected.get(item.getName());
            if (golden == null) continue;
            int used      = Integer.parseInt(item.getUsed().replace("0x", ""), 16);
            int available = Integer.parseInt(item.getLength().replace("0x", ""), 16);
            assertEquals(item.getName() + " used",      golden[0], used);
            assertEquals(item.getName() + " available", golden[1], available);
            seen++;
        }
        assertEquals("Should have parsed all expected rows", expected.size(), seen);
    }
}
