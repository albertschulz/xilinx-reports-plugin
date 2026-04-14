package org.jenkinsci.plugins.xilinx.timing;

import hudson.model.Action;
import hudson.model.Run;
import net.praqma.jenkins.memorymap.MemoryMapBuildAction;
import net.praqma.jenkins.memorymap.result.MemoryMapConfigMemory;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;

public class VivadoTimingBuildAction extends MemoryMapBuildAction {
    private List<VivadoTimingProjectAction> projectActions;

    public VivadoTimingBuildAction(Run<?, ?> build, HashMap<String, MemoryMapConfigMemory> memoryMapConfig) {
        super(build, memoryMapConfig);
        projectActions = new ArrayList<>();
        projectActions.add(new VivadoTimingProjectAction(build.getParent()));
    }

    @Override
    public String getIconFileName() {
        return null;
    }

    @Override
    public String getDisplayName() {
        return "Xilinx Timing";
    }

    @Override
    public String getUrlName() {
        return null;
    }

    @Override
    public Collection<? extends Action> getProjectActions() {
        return projectActions;
    }
}
