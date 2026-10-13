param([string]$TraceRoot = "$PSScriptRoot/../run/v5-benchmark")
$ErrorActionPreference = 'Stop'
$rows = foreach ($trace in Get-ChildItem -LiteralPath $TraceRoot -Filter frames.csv -Recurse) {
    [double[]]$samples = @(Import-Csv -LiteralPath $trace.FullName | ForEach-Object { [double]$_.ns / 1000000 })
    if ($samples.Count -eq 0) { continue }
    [Array]::Sort($samples)
    $slowCount = [Math]::Max(1, [int][Math]::Ceiling($samples.Count * .01))
    $slow = $samples[($samples.Count - $slowCount)..($samples.Count - 1)] | Measure-Object -Average
    $stats = $samples | Measure-Object -Average -Sum
    $environment = Get-Content -LiteralPath (Join-Path $trace.DirectoryName 'environment.txt') -Raw
    [pscustomobject]@{
        Run = $trace.Directory.Name
        Frames = $samples.Count
        DurationSeconds = $stats.Sum / 1000
        AverageFPS = 1000 / $stats.Average
        OnePercentLowFPS = 1000 / $slow.Average
        P50Ms = $samples[[Math]::Ceiling($samples.Count * .50) - 1]
        P95Ms = $samples[[Math]::Ceiling($samples.Count * .95) - 1]
        P99Ms = $samples[[Math]::Ceiling($samples.Count * .99) - 1]
        MaxMs = $samples[-1]
        Invalid = $environment.Contains('overflow=true') -or $environment.Contains('unfocused=true')
    }
}
$rows | Export-Csv -LiteralPath (Join-Path $TraceRoot 'summary.csv') -NoTypeInformation
$rows | Format-Table Run, AverageFPS, OnePercentLowFPS, P95Ms, P99Ms, Invalid
