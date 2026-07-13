param(
  [string]$StatusFile = "delivery/status.yaml",
  [string]$IterationDir = "delivery/iterations",
  [string]$TaskDir = "delivery/tasks"
)

Write-Host "=== Delivery Control Plane Validation (Node.js backend) ===" -ForegroundColor Cyan

$nodeCmd = "node scripts/delivery/validate.mjs ""$StatusFile"" ""$IterationDir"" ""$TaskDir"""
$output = & cmd /c $nodeCmd 2>&1
$exitCode = $LASTEXITCODE

foreach ($line in $output) { Write-Host $line }

if ($exitCode -eq 0) {
  Write-Host "`nValidator reports exit code 0" -ForegroundColor Green
} else {
  Write-Host "`nValidator reports exit code $exitCode" -ForegroundColor Red
}

exit $exitCode
