package org.jenkinsci.plugins.xilinx.timing;

import hudson.Extension;
import hudson.model.Descriptor;
import net.praqma.jenkins.memorymap.graph.MemoryMapGraphConfiguration;
import net.praqma.jenkins.memorymap.parser.AbstractMemoryMapParser;
import net.praqma.jenkins.memorymap.parser.MemoryMapParserDescriptor;
import net.praqma.jenkins.memorymap.result.MemoryMapConfigMemory;
import net.praqma.jenkins.memorymap.result.MemoryMapConfigMemoryItem;
import net.sf.json.JSONObject;
import org.jenkinsci.Symbol;
import org.kohsuke.stapler.DataBoundConstructor;
import org.kohsuke.stapler.StaplerRequest;

import java.io.File;
import java.io.IOException;
import java.io.Serializable;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.logging.Logger;
import java.util.regex.Matcher;
import java.util.regex.Pattern;


@Extension
public class VivadoTimingParser extends AbstractMemoryMapParser implements Serializable {
    private static final Logger LOG = Logger.getLogger(VivadoTimingParser.class.getName());
    public static final int RADIX = 10;

    // Slack values are stored in picoseconds so we can keep the integer-valued data
    // model the upstream memory-map plugin expects. SLACK_CEILING_PS picks an upper
    // bound that comfortably covers any real-world clock period (10 ns) so the
    // Util% column in Jenkins remains in [0, 100] rather than overflowing.
    public static final int SLACK_CEILING_PS = 10000;

    private static final ArrayList<MemoryMapGraphConfiguration> defaultGraphConfiguration = new ArrayList<MemoryMapGraphConfiguration>() {{
        add(new MemoryMapGraphConfiguration("WNS_ps,WHS_ps", "Slack (ps)"));
    }};

    @DataBoundConstructor
    public VivadoTimingParser(String parserUniqueName, String parserTitle, String report, List<MemoryMapGraphConfiguration> graphConfiguration) {
        super(parserUniqueName, report, report, RADIX, false, graphConfiguration);
        setParserTitle(parserTitle);
    }

    public static List<MemoryMapGraphConfiguration> getDefaultGraphConfig() {
        return Collections.unmodifiableList(defaultGraphConfiguration);
    }

    public String getReport() {
        return super.getConfigurationFile();
    }

    public VivadoTimingParser() {
        super();
    }

    /*
       The "Design Timing Summary" section in *_timing_summary_routed.rpt looks like:

       ------------------------------------------------------------------------------
       | Design Timing Summary
       | ---------------------
       ------------------------------------------------------------------------------

           WNS(ns)      TNS(ns)  TNS Failing Endpoints  TNS Total Endpoints      WHS(ns)      THS(ns)  ...
           -------      -------  ---------------------  -------------------      -------      -------  ...
             0.296        0.000                      0                 5471        0.012        0.000  ...

       Subsequent sections (Clock Summary, Intra Clock Table, ...) reuse the same
       column headers, so we anchor strictly on the "Design Timing Summary" title
       line and walk forward from there.
     */
    private static final Pattern DESIGN_TIMING_SUMMARY = Pattern.compile(
            "\\|\\s*Design Timing Summary\\s*\\R" +
            "\\|\\s*-+\\s*\\R" +
            "-+\\s*\\R" +
            "\\s*\\R" +
            "(?<header>[^\\n]*WNS\\(ns\\)[^\\n]*)\\R" +
            "(?<divider>\\s*-[\\s-]*)\\R" +
            "(?<data>[^\\n]+)\\R");

    public MemoryMapConfigMemory getResources(File f) throws IOException {
        CharSequence seq = createCharSequenceFromFile(f);
        MemoryMapConfigMemory items = new MemoryMapConfigMemory();

        Matcher m = DESIGN_TIMING_SUMMARY.matcher(seq);
        if (!m.find()) {
            LOG.warning("Design Timing Summary section not found in " + f.getName());
            return items;
        }

        String[] cells = m.group("data").trim().split("\\s+");
        if (cells.length < 6) {
            LOG.warning("Design Timing Summary data row has only " + cells.length + " columns in " + f.getName());
            return items;
        }

        // Column order is fixed by Vivado: 0=WNS, 1=TNS, 2=TNS-fail-eps, 3=TNS-total-eps, 4=WHS, 5=THS, ...
        addSlack(items, "WNS_ps", cells[0]);
        addSlack(items, "WHS_ps", cells[4]);

        return items;
    }

    private static void addSlack(MemoryMapConfigMemory items, String name, String nsValueStr) {
        int picoseconds;
        try {
            double ns = Double.parseDouble(nsValueStr);
            picoseconds = (int) Math.round(ns * 1000.0);
        } catch (NumberFormatException ex) {
            LOG.warning("Could not parse timing value '" + nsValueStr + "' for " + name);
            return;
        }
        if (picoseconds < 0) {
            // Negative slack means timing failed. The build script normally aborts before
            // reaching the reporting step, but if we ever land here, clamp to 0 so the
            // graph stays meaningful instead of plotting a giant negative spike.
            LOG.warning(name + " is negative (" + picoseconds + " ps); timing not met, clamping to 0");
            picoseconds = 0;
        }
        int unused = SLACK_CEILING_PS - picoseconds;
        items.add(new MemoryMapConfigMemoryItem(name, "0",
                "0x" + Integer.toHexString(SLACK_CEILING_PS),
                "0x" + Integer.toHexString(picoseconds),
                "0x" + Integer.toHexString(unused)));
    }

    @Override
    public MemoryMapConfigMemory parseMapFile(File f, MemoryMapConfigMemory configuration) throws IOException {
        return getResources(f);
    }

    @Override
    public MemoryMapConfigMemory parseConfigFile(File f) throws IOException {
        return getResources(f);
    }

    @Override
    /**
     * This is actually used as the radix
     */
    public int getDefaultWordSize() {
        return RADIX;
    }

    @Symbol("xilinxTimingParser")
    @Extension
    public static final class DescriptorImpl extends MemoryMapParserDescriptor<VivadoTimingParser> {

        @Override
        public String getDisplayName() {
            return "Xilinx Timing";
        }

        @Override
        public AbstractMemoryMapParser newInstance(StaplerRequest req, JSONObject formData, AbstractMemoryMapParser instance) throws Descriptor.FormException {
            VivadoTimingParser parser = (VivadoTimingParser) instance;
            save();
            return parser;
        }
    }
}
