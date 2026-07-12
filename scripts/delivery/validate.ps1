param(
  [string]$StatusFile = "delivery/status.yaml",
  [string]$IterationDir = "delivery/iterations",
  [string]$TaskDir = "delivery/tasks"
)

$exitCode = 0
$errors = @()

Write-Host "=== Delivery Control Plane Validation ==="

# 1. SHA validation - all baselineCommit values must be 40-char hex
Write-Host "`n[CHECK] SHA length (must be 40-char hex)..."
$content = Get-Content -Raw $StatusFile
$shaMatches = [regex]::Matches($content, 'baselineCommit:\s+"([^"]+)"')
foreach ($m in $shaMatches) {
  $sha = $m.Groups[1].Value
  if ($sha -ne "null" -and $sha.Length -ne 40) {
    $errors += "SHA length violation: '$sha' is $($sha.Length) chars (expected 40)"
    $exitCode = 1
  }
}

# 2. Collect actual task states from the tasks section
Write-Host "`n[CHECK] Task state summary consistency..."
$taskStates = @()
$taskSection = [regex]::Match($content, '(?s)tasks:\s*\n(.*?)summary:')
if ($taskSection.Success) {
  $taskBody = $taskSection.Groups[1].Value
  $stateMatches = [regex]::Matches($taskBody, 'state:\s+"([^"]+)"')
  foreach ($sm in $stateMatches) { $taskStates += $sm.Groups[1].Value }
}

$validStates = @("BACKLOG","READY","CLAIMED","IMPLEMENTING","SELF_VERIFIED",
                 "INDEPENDENT_REVIEW","CI_PENDING","HUMAN_REVIEW","MERGED",
                 "VERIFIED","FIX_REQUIRED","BLOCKED","CLARIFICATION_REQUIRED",
                 "SPEC_CONFLICT","SUPERSEDED","CANCELLED")

foreach ($s in $validStates) {
  $actualCount = ($taskStates | Where-Object {$_ -eq $s}).Count
  $pattern = "(?m)^\s+$s\:\s+(\d+)"
  $reCount = [regex]::Match($content, $pattern)
  $declaredCount = 0
  if ($reCount.Success) { $declaredCount = [int]$reCount.Groups[1].Value }
  if ($actualCount -ne $declaredCount) {
    $errors += "Count mismatch for '$s': actual=$actualCount declared=$declaredCount"
    $exitCode = 1
  }
}

# 3. Handoff file references exist
Write-Host "`n[CHECK] Handoff file references..."
$handoffRefs = [regex]::Matches($content, 'latestHandoff:\s+"([^"]+)"')
foreach ($hr in $handoffRefs) {
  $path = $hr.Groups[1].Value
  if ($path -ne "null" -and -not (Test-Path $path)) {
    $errors += "Missing handoff file: $path"
    $exitCode = 1
  }
}

# 4. Iteration task references exist as task YAML files
Write-Host "`n[CHECK] Iteration task references..."
if (Test-Path $IterationDir) {
  $iterFiles = Get-ChildItem "$IterationDir/*.yaml"
  foreach ($f in $iterFiles) {
    $iterContent = Get-Content -Raw $f.FullName
    $taskSection = [regex]::Match($iterContent, '(?s)tasks:\s*\n((?:\s+-\s+"[^"]+"\s*\n)*)')
    if (-not $taskSection.Success) { continue }
    $taskMatches = [regex]::Matches($taskSection.Groups[1].Value, '-\s+"([^"]+)"')
    foreach ($tm in $taskMatches) {
      $taskId = $tm.Groups[1].Value
      $taskFile = Join-Path $TaskDir "$taskId.yaml"
      if (-not (Test-Path $taskFile)) {
        $errors += "Iteration '$($f.BaseName)' references '$taskId' but $taskFile not found"
        $exitCode = 1
      }
    }
  }
}

if ($errors.Count -eq 0) {
  Write-Host "`n=== ALL CHECKS PASSED ===" -ForegroundColor Green
} else {
  Write-Host "`n=== $($errors.Count) FAILURE(S) ===" -ForegroundColor Red
  foreach ($e in $errors) { Write-Host "  FAIL: $e" -ForegroundColor Red }
}

exit $exitCode
