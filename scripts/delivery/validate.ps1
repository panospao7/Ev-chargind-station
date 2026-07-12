param(
  [string]$StatusFile = "delivery/status.yaml",
  [string]$IterationDir = "delivery/iterations",
  [string]$TaskDir = "delivery/tasks"
)

$exitCode = 0
$errors = @()

Write-Host "=== Delivery Control Plane Validation ==="

if (-not (Test-Path $StatusFile)) {
  Write-Host "FATAL: Status file not found: $StatusFile" -ForegroundColor Red
  exit 1
}
$content = Get-Content -Raw $StatusFile
$lines = $content -split "`r`n|`n"

# ---- 1. Duplicate YAML key detection via scope-aware tracker ----
Write-Host "`n[CHECK] Duplicate YAML keys..."

function Find-DuplicateYamlKeys {
  param([string[]]$Lines)
  $errs = @()

  # Stack of hashtables: each entry is @{ Indent = N; Keys = @{} }
  $stack = New-Object System.Collections.ArrayList

  for ($i = 0; $i -lt $Lines.Length; $i++) {
    $line = $Lines[$i]
    if ($line -match '^\s*$' -or $line -match '^\s*#') { continue }

    if ($line -match '^(\s*)(\S[\w/.-]*)\s*:\s*(.*)') {
      $indent = $matches[1].Length
      $key    = $matches[2]
      $value  = $matches[3].Trim()
      $lineNo = $i + 1

      # Pop any scopes whose indent is >= current key indent
      while ($stack.Count -gt 0 -and $stack[$stack.Count-1].Indent -ge $indent) {
        $null = $stack.RemoveAt($stack.Count-1)
      }

      # The current scope (if any) is the top of stack after popping
      $currentScope = if ($stack.Count -gt 0) { $stack[$stack.Count-1].Keys } else { $null }

      if ($currentScope -ne $null -and $currentScope.ContainsKey($key)) {
        $errs += "Duplicate key '$key' at indent $indent (line $lineNo)"
      }

      if ($value -eq "" -or $value -eq "{" -or $value -match "^\[") {
        $newScope = @{ Indent = $indent; Keys = @{} }
        $newScope.Keys[$key] = $true
        if ($currentScope -ne $null) { $currentScope[$key] = $true }
        $null = $stack.Add($newScope)
      } else {
        if ($currentScope -ne $null) { $currentScope[$key] = $true }
      }
    }
  }
  return $errs
}

$dupErrors = Find-DuplicateYamlKeys -Lines $lines
foreach ($e in $dupErrors) { $errors += $e; $exitCode = 1 }

# ---- 2. SHA hex content and length validation ----
Write-Host "`n[CHECK] SHA hex content (40-char, hex only)..."
$shaPatterns = @('baselineCommit', 'candidateCommit')
foreach ($p in $shaPatterns) {
  $matches = [regex]::Matches($content, "$p\:\s+""([^""]+)""")
  foreach ($m in $matches) {
    $sha = $m.Groups[1].Value
    if ($sha -eq "null") { continue }
    if ($sha.Length -ne 40) {
      $errors += "SHA length violation in '$p': '$sha' is $($sha.Length) chars (expected 40)"
      $exitCode = 1
    } elseif ($sha -notmatch '^[0-9a-f]{40}$') {
      $errors += "SHA hex violation in '$p': '$sha' contains non-hex characters"
      $exitCode = 1
    }
  }
}

# ---- 3. Task state summary consistency + unknown state detection ----
Write-Host "`n[CHECK] Task state consistency..."
$taskStates = @()
$taskSection = [regex]::Match($content, '(?s)tasks:\s*\n(.*?)(?=\n\S|\Z)')
if ($taskSection.Success) {
  $taskBody = $taskSection.Groups[1].Value
  $stateMatches = [regex]::Matches($taskBody, 'state:\s+"([^"]+)"')
  foreach ($sm in $stateMatches) { $taskStates += $sm.Groups[1].Value }
}

$validStates = @("BACKLOG","READY","CLAIMED","IMPLEMENTING","SELF_VERIFIED",
                 "INDEPENDENT_REVIEW","CI_PENDING","HUMAN_REVIEW","MERGED",
                 "VERIFIED","FIX_REQUIRED","BLOCKED","CLARIFICATION_REQUIRED",
                 "SPEC_CONFLICT","SUPERSEDED","CANCELLED")

# Unknown state detection
$foundUnknown = $false
foreach ($ts in $taskStates) {
  if ($ts -notin $validStates) {
    $errors += "Unknown task state '$ts'"
    $foundUnknown = $true
    $exitCode = 1
  }
}
if (-not $foundUnknown) { Write-Host "  All states valid" } else { Write-Host "  Found unknown state(s)" }

# Count consistency
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

# ---- 4. Handoff file references exist ----
Write-Host "`n[CHECK] Handoff file references..."
$handoffRefs = [regex]::Matches($content, 'latestHandoff:\s+"([^"]+)"')
$handoffOk = $true
foreach ($hr in $handoffRefs) {
  $path = $hr.Groups[1].Value
  if ($path -ne "null" -and -not (Test-Path $path)) {
    $errors += "Missing handoff file: $path"
    $handoffOk = $false
    $exitCode = 1
  }
}
if ($handoffOk) { Write-Host "  All handoff references valid" }

# ---- 5. Iteration task references exist as task YAML files ----
Write-Host "`n[CHECK] Iteration task references..."
$iterOk = $true
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
        $iterOk = $false
        $exitCode = 1
      }
    }
  }
}
if ($iterOk) { Write-Host "  All iteration task references valid" }

# ---- 6. Summary counts all present ----
Write-Host "`n[CHECK] All expected summary state keys present..."
$summarySection = [regex]::Match($content, '(?s)summary:\s*\n\s+counts:\s*\n(.*?)(?=\n\S|\Z)')
if ($summarySection.Success) {
  $summaryBlock = $summarySection.Groups[1].Value
  $summaryOk = $true
  foreach ($s in $validStates) {
    if ($summaryBlock -notmatch "(?m)^\s+$s\:\s+\d+") {
      $errors += "Missing summary count for state '$s'"
      $summaryOk = $false
      $exitCode = 1
    }
  }
  if ($summaryOk) { Write-Host "  All 15 summary state keys present" }
}

if ($errors.Count -eq 0) {
  Write-Host "`n=== ALL CHECKS PASSED ===" -ForegroundColor Green
} else {
  Write-Host "`n=== $($errors.Count) FAILURE(S) ===" -ForegroundColor Red
  foreach ($e in $errors) { Write-Host "  FAIL: $e" -ForegroundColor Red }
}

exit $exitCode
