package org.jenkinsci.plugins.xilinx.timing;

import hudson.Extension;
import javaposse.jobdsl.dsl.Context;
import javaposse.jobdsl.dsl.RequiresPlugin;
import javaposse.jobdsl.dsl.helpers.publisher.PublisherContext;
import javaposse.jobdsl.plugin.ContextExtensionPoint;
import javaposse.jobdsl.plugin.DslExtensionMethod;
import net.praqma.jenkins.memorymap.graph.MemoryMapGraphConfiguration;

import java.util.ArrayList;
import java.util.List;

/*
```
job {
    publishers {
        xilinxTiming {
            reportName (String reportName)
            graphConfigs {
                graphCaption  (String graphCaption)
                graphDataList (String graphDataList)
            }
        }
    }
}
```

```
job ('timing_GEN'){
    publishers {
        xilinxTiming {
            reportName 'system_test_top_timing_summary_routed.rpt'
            graph {
                graphCaption  'Slack (ps)'
                graphDataList 'WNS_ps,WHS_ps'
            }
        }
    }
}
```
*/

@Extension(optional = true)
public class VivadoTimingDslExtension extends ContextExtensionPoint {

    @RequiresPlugin(id = "xilinx-reports", minimumVersion = "0.1")
    @DslExtensionMethod(context = PublisherContext.class)
    public Object xilinxTiming(Runnable closure) {
        VivadoTimingJobDslContext context = new VivadoTimingJobDslContext();
        executeInContext(closure, context);

        return new VivadoTimingBuildStep(context.reportName, context.graphConfigs);
    }


    public class VivadoTimingJobDslContext implements Context {
        String reportName = "timing_summary.rpt";
        List<MemoryMapGraphConfiguration> graphConfigs = new ArrayList<>();

        public void reportName(String value) {
            reportName = value;
        }

        public void graph(Runnable closure) {
            VivadoTimingGraphDslContext context = new VivadoTimingGraphDslContext();
            executeInContext(closure, context);

            graphConfigs.add(new MemoryMapGraphConfiguration(context.graphDataList, context.graphCaption));
        }

    }

    public class VivadoTimingGraphDslContext implements Context {
        String graphDataList;
        String graphCaption;

        public VivadoTimingGraphDslContext() {
        }

        public void graphDataList(String value) {
            this.graphDataList = value;
        }

        public void graphCaption(String value) {
            this.graphCaption = value;
        }
    }
}
