package org.jenkinsci.plugins.xilinx.timing;

import hudson.model.Job;
import hudson.model.Run;
import net.praqma.jenkins.memorymap.MemoryMapBuildAction;
import net.praqma.jenkins.memorymap.MemoryMapProjectAction;

public class VivadoTimingProjectAction extends MemoryMapProjectAction {

    public VivadoTimingProjectAction(Job<?, ?> project) {
        super(project);
    }

    @Override
    public String getDisplayName() {
        return "Xilinx Timing";
    }

    @Override
    public String getSearchUrl() {
        return "Xilinx Timing";
    }

    @Override
    public String getIconFileName() {
        return ICON_NAME;
    }

    @Override
    public String getUrlName() {
        return "xilinx-timing";
    }

    // See VivadoUtilizationProjectAction for the rationale — pin lookups to our concrete
    // subclass so the Timing page never picks up Utilization data and vice versa.
    @Override
    public MemoryMapBuildAction getLatestActionInProject() {
        Run<?, ?> last = getProject().getLastCompletedBuild();
        return last == null ? null : last.getAction(VivadoTimingBuildAction.class);
    }

    @Override
    public MemoryMapBuildAction getLastApplicableMemoryMapResult() {
        for (Run<?, ?> run = getProject().getLastCompletedBuild(); run != null; run = run.getPreviousBuild()) {
            VivadoTimingBuildAction a = run.getAction(VivadoTimingBuildAction.class);
            if (a != null && a.isValidConfigurationWithData()) {
                return a;
            }
        }
        return null;
    }
}
