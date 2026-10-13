param([string]$World = 'Optic V4 Validation', [ValidateRange(5,300)][int]$Seconds = 30)
$ErrorActionPreference = 'Stop'
Push-Location (Join-Path $PSScriptRoot '..')
try {
    # Seeded randomized ordering, paired repeats. This does not benchmark OBS or mod-disabled.
    $rng = [Random]::new(51)
    foreach ($repeat in 1..3) {
        $order = if ($rng.Next(2) -eq 0) { @('idle','record') } else { @('record','idle') }
        foreach ($mode in $order) {
            $label = "$mode-$repeat"
            & ./gradlew.bat runClient "-Pbenchmark=$mode" "-PbenchmarkWorld=$World" "-PbenchmarkSeconds=$Seconds" "-PbenchmarkLabel=$label" *> "build/v5-$label.log"
            if ($LASTEXITCODE -ne 0) { throw "Client failed: $label. See build/v5-$label.log" }
            if (-not (Select-String -LiteralPath "build/v5-$label.log" -Pattern 'OPTIC_BENCHMARK_FINISHED' -Quiet)) { throw "No completed trace: $label" }
        }
    }
    & "$PSScriptRoot/Summarize.ps1"
} finally { Pop-Location }
