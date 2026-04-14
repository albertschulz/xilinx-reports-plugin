# xilinx-reports Jenkins plugin

Jenkins plugin for reporting on Xilinx Vivado FPGA builds. Parses
`report_utilization` output for resource usage (LUTs, FFs, BRAM, DSP, URAM),
parses `report_timing_summary` output for worst-case slack (WNS, WHS), and
filters Vivado logs into separate Info/Warning/Error streams.

This is the albertschulz fork of [derrickgw/xilinx-reports-plugin](https://github.com/derrickgw/xilinx-reports-plugin)
(dormant since 2019). Changes versus upstream 0.6:

- **Timing parser** — new `xilinxTiming` pipeline step / *Xilinx Timing
  Publisher* post-build action. Parses the `Design Timing Summary` block in
  `*_timing_summary_routed.rpt` and surfaces `WNS_ps` and `WHS_ps` (nanoseconds
  × 1000, since the underlying memory-map data model is unsigned-int-only).
- **Vivado 2020+ utilization fix** — upstream matched only the old 5-column
  `| Site Type | Used | Fixed | Available | Util% |` format. Vivado 2020 added
  a `Prohibited` column, so modern reports were parsed as zero rows. Rows of
  either width are now accepted.
- **Project-action cross-contamination fix** — both build actions inherit from
  `MemoryMapBuildAction`, so the parent's `Run.getAction(MemoryMapBuildAction.class)`
  lookup returned whichever subclass happened to be attached first. Each
  project page now pins lookups to its own concrete subclass, so *Xilinx
  Utilization* only shows utilization data and *Xilinx Timing* only shows
  timing data.

## Building

Requires Java 11 — the upstream POM is from 2019 and uses Maven plugins that
break under Java 17+.

```
JAVA_HOME=/usr/lib/jvm/java-11-openjdk-amd64 \
PATH=$JAVA_HOME/bin:$PATH \
mvn -DskipTests package
# -> target/xilinx-reports.hpi
```

`-DskipTests` is recommended because one of the upstream integration tests
(`VivadoUtilizationBuildStepTest.testScriptedPipeline`) hangs for three
minutes before timing out. The parser unit tests can be run individually:

```
mvn -Dtest=VivadoUtilizationParserTest,VivadoTimingParserTest test
```

## Pipeline usage

Utilization (from `*_utilization_placed.rpt`):

```
post {
    always {
        step([$class: 'VivadoUtilizationBuildStep',
              chosenParsers: [[$class: 'VivadoUtilizationParser',
                  parserUniqueName: 'my_util',
                  parserTitle: 'Utilization',
                  report: 'path/to/*_utilization_placed.rpt',
                  graphConfiguration: [
                      [$class: 'MemoryMapGraphConfiguration', graphCaption: 'CLB',  graphDataList: 'CLB_LUTs,CLB_Registers'],
                      [$class: 'MemoryMapGraphConfiguration', graphCaption: 'BRAM', graphDataList: 'Block_RAM_Tile'],
                      [$class: 'MemoryMapGraphConfiguration', graphCaption: 'DSP',  graphDataList: 'DSPs']
                  ]
              ]]
        ])
    }
}
```

Timing (from `*_timing_summary_routed.rpt`):

```
step([$class: 'VivadoTimingBuildStep',
      chosenParsers: [[$class: 'VivadoTimingParser',
          parserUniqueName: 'my_timing',
          parserTitle: 'Timing',
          report: 'path/to/*_timing_summary_routed.rpt',
          graphConfiguration: [
              [$class: 'MemoryMapGraphConfiguration', graphCaption: 'Slack (ps)', graphDataList: 'WNS_ps,WHS_ps']
          ]
      ]]
])
```

The older upstream-style symbol invocations also still work:

```
xilinxUtilization([
    xilinxParser(
        report: 'utilization.rpt', parserTitle: 'Utilization', parserUniqueName: 'my_util',
        graphConfiguration: [
            [graphCaption: 'BRAMs',  graphDataList: 'Block_RAM_Tile'],
            [graphCaption: 'Slices', graphDataList: 'CLB_LUTs,CLB_Registers'],
            [graphCaption: 'DSPs',   graphDataList: 'DSPs']
        ]
    )
])
```

## Job DSL

```
publishers {
  xilinxUtilization {
    reportName '**/*_utilization_placed.rpt'
    graph { graphCaption 'DSPs'   graphDataList 'DSPs' }
    graph { graphCaption 'BRAM'   graphDataList 'Block_RAM_Tile' }
    graph { graphCaption 'Slices' graphDataList 'CLB_LUTs,CLB_Registers' }
  }
  xilinxTiming {
    reportName '**/*_timing_summary_routed.rpt'
    graph { graphCaption 'Slack (ps)' graphDataList 'WNS_ps,WHS_ps' }
  }
}
```

## Metric names

Names in `graphDataList` must match what the parser emits:

- **Utilization** — any left-column entry of any `| Site Type | Used | ... |`
  table in the report, with spaces replaced by underscores. Common ones:
  `CLB_LUTs`, `CLB_Registers`, `Register_as_Flip_Flop`, `Block_RAM_Tile`,
  `DSPs`, `URAM`, `Bonded_IOB`, `BUFGCE`, `MMCM`, `GTHE4_CHANNEL`.
- **Timing** — `WNS_ps`, `WHS_ps`. Picoseconds because the memory-map data
  model is unsigned-int-only; divide the reported value by 1000 for
  nanoseconds. A synthetic ceiling of 10000 ps (10 ns) is used so the Util%
  column remains in [0, 100]. Negative slack (timing failure) is clamped to
  0 with a log warning.

## Vivado warnings

```
recordIssues tools: [[$class: 'VivadoWarningsTool', pattern: 'vivado.log']],
    filters: [
        excludeFile('.*/prj/vivado/.*\.srcs/sources_1/ip/.*'),
        excludeType('Synth 8-6014')
    ]
```
