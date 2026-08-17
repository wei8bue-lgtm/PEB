param(
    [Parameter(Mandatory = $true)]
    [string]$Org,

    [Parameter(Mandatory = $true)]
    [string]$Project,

    [Parameter(Mandatory = $true)]
    [int]$WorkItemId,

    [Parameter(Mandatory = $true)]
    [string]$NewState,

    [Parameter(Mandatory = $true)]
    [string]$Comment,

    [Parameter(Mandatory = $true)]
    [string]$RepoPath,

    [Parameter(Mandatory = $true)]
    [string]$BranchName,

    [Parameter(Mandatory = $true)]
    [string]$CommitMessage,

    [string]$ImplementationCommand = "",
    [string]$BuildCommand = "mvn -q -DskipTests compile",
    [string]$TestCommand = "mvn -q test",
    [string]$TargetBranch = "main",
    [string]$Reason = "",
    [switch]$AutoCreatePR,
    [switch]$SkipBuild,
    [switch]$SkipTests,
    [switch]$OnPrem,
    [switch]$DryRun
)

$ErrorActionPreference = "Stop"

function Get-Pat {
    if ($env:AZDO_PAT -and $env:AZDO_PAT.Trim().Length -gt 0) {
        return $env:AZDO_PAT
    }

    $securePat = Read-Host "Please enter Azure DevOps PAT" -AsSecureString
    $bstr = [Runtime.InteropServices.Marshal]::SecureStringToBSTR($securePat)
    try {
        return [Runtime.InteropServices.Marshal]::PtrToStringBSTR($bstr)
    }
    finally {
        [Runtime.InteropServices.Marshal]::ZeroFreeBSTR($bstr)
    }
}

function Get-BaseUrl {
    param(
        [string]$Org,
        [switch]$OnPrem
    )

    if ($OnPrem) {
        return "https://rb-devops.bosch.com/$Org"
    }

    return "https://dev.azure.com/$Org"
}

function Invoke-CommandChecked {
    param(
        [string]$Command,
        [string]$WorkingDirectory,
        [string]$StepName
    )

    Write-Host ""
    Write-Host "[$StepName] $Command"

    if ($DryRun) {
        Write-Host "[DryRun] Command skipped"
        return
    }

    Push-Location $WorkingDirectory
    try {
        Invoke-Expression $Command
        if ($LASTEXITCODE -ne 0) {
            throw "Step '$StepName' failed with exit code $LASTEXITCODE"
        }
    }
    finally {
        Pop-Location
    }
}

function Invoke-Git {
    param(
        [string[]]$Args,
        [string]$RepoPath,
        [string]$StepName
    )

    Write-Host ""
    Write-Host "[$StepName] git $($Args -join ' ')"

    if ($DryRun) {
        Write-Host "[DryRun] Git command skipped"
        return ""
    }

    $output = & git -C $RepoPath @Args
    if ($LASTEXITCODE -ne 0) {
        throw "Git step '$StepName' failed with exit code $LASTEXITCODE"
    }

    if ($null -ne $output) {
        return ($output | Out-String).Trim()
    }

    return ""
}

function Update-WorkItem {
    param(
        [string]$BaseUrl,
        [string]$Project,
        [int]$WorkItemId,
        [string]$State,
        [string]$Comment,
        [string]$Reason,
        [hashtable]$Headers
    )

    $apiVersion = "7.1"
    $url = "$BaseUrl/$Project/_apis/wit/workitems/$WorkItemId?api-version=$apiVersion"

    $patchOps = @(
        @{
            op = "add"
            path = "/fields/System.History"
            value = $Comment
        }
    )

    if ($State -and $State.Trim().Length -gt 0) {
        $patchOps += @{
            op = "add"
            path = "/fields/System.State"
            value = $State
        }
    }

    if ($Reason -and $Reason.Trim().Length -gt 0) {
        $patchOps += @{
            op = "add"
            path = "/fields/System.Reason"
            value = $Reason
        }
    }

    $body = $patchOps | ConvertTo-Json -Depth 5

    if ($DryRun) {
        Write-Host ""
        Write-Host "[DryRun] Work item PATCH skipped"
        Write-Host $body
        return
    }

    Invoke-RestMethod -Method Patch -Uri $url -Headers $Headers -ContentType "application/json-patch+json" -Body $body | Out-Null
}

function Get-WorkItem {
    param(
        [string]$BaseUrl,
        [string]$Project,
        [int]$WorkItemId,
        [hashtable]$Headers
    )

    $apiVersion = "7.1"
    $url = "$BaseUrl/$Project/_apis/wit/workitems/$WorkItemId?api-version=$apiVersion"
    return Invoke-RestMethod -Method Get -Uri $url -Headers $Headers
}

try {
    if (-not (Test-Path $RepoPath)) {
        throw "RepoPath does not exist: $RepoPath"
    }

    if (-not (Test-Path (Join-Path $RepoPath ".git"))) {
        throw "RepoPath is not a git repository: $RepoPath"
    }

    if (-not $ImplementationCommand -or $ImplementationCommand.Trim().Length -eq 0) {
        throw "ImplementationCommand is required for Option 3b. Example: -ImplementationCommand 'mvn -q -DskipTests package' or custom script command"
    }

    $pat = Get-Pat
    $baseUrl = Get-BaseUrl -Org $Org -OnPrem:$OnPrem

    $auth = [Convert]::ToBase64String([Text.Encoding]::ASCII.GetBytes(":$pat"))
    $headers = @{ Authorization = "Basic $auth" }

    Write-Host "=== Option 3b Started ==="
    Write-Host "Work Item: $WorkItemId"
    Write-Host "Project: $Project"
    Write-Host "RepoPath: $RepoPath"

    $workItem = Get-WorkItem -BaseUrl $baseUrl -Project $Project -WorkItemId $WorkItemId -Headers $headers
    $title = $workItem.fields."System.Title"
    $currentState = $workItem.fields."System.State"

    Write-Host ""
    Write-Host "Work Item Title : $title"
    Write-Host "Current State   : $currentState"

    Invoke-Git -Args @("fetch", "--all", "--prune") -RepoPath $RepoPath -StepName "Git fetch" | Out-Null
    Invoke-Git -Args @("checkout", "-b", $BranchName) -RepoPath $RepoPath -StepName "Create branch" | Out-Null

    Invoke-CommandChecked -Command $ImplementationCommand -WorkingDirectory $RepoPath -StepName "Implementation"

    if (-not $SkipBuild) {
        Invoke-CommandChecked -Command $BuildCommand -WorkingDirectory $RepoPath -StepName "Build"
    }

    if (-not $SkipTests) {
        Invoke-CommandChecked -Command $TestCommand -WorkingDirectory $RepoPath -StepName "Tests"
    }

    $status = Invoke-Git -Args @("status", "--porcelain") -RepoPath $RepoPath -StepName "Check changes"
    if (-not $status -or $status.Trim().Length -eq 0) {
        throw "No changes detected after ImplementationCommand. Nothing to commit."
    }

    Invoke-Git -Args @("add", "-A") -RepoPath $RepoPath -StepName "Git add" | Out-Null
    Invoke-Git -Args @("commit", "-m", $CommitMessage) -RepoPath $RepoPath -StepName "Git commit" | Out-Null
    Invoke-Git -Args @("push", "-u", "origin", $BranchName) -RepoPath $RepoPath -StepName "Git push" | Out-Null

    $commitHash = Invoke-Git -Args @("rev-parse", "HEAD") -RepoPath $RepoPath -StepName "Get commit hash"
    $remoteUrl = Invoke-Git -Args @("remote", "get-url", "origin") -RepoPath $RepoPath -StepName "Get remote"

    $prUrl = ""
    if ($AutoCreatePR) {
        if ($DryRun) {
            Write-Host "[DryRun] PR creation skipped"
        }
        else {
            $null = Get-Command az -ErrorAction Stop

            $prTitle = "WI#$WorkItemId $title"
            $prDescription = "Automated PR for Work Item $WorkItemId"

            $prCreate = @(
                "repos", "pr", "create",
                "--org", $baseUrl,
                "--project", $Project,
                "--source-branch", $BranchName,
                "--target-branch", $TargetBranch,
                "--title", $prTitle,
                "--description", $prDescription,
                "--work-items", "$WorkItemId",
                "--output", "json"
            )

            $prRaw = & az @prCreate
            if ($LASTEXITCODE -ne 0) {
                throw "PR creation failed via Azure CLI"
            }

            $prObj = $prRaw | ConvertFrom-Json
            $prUrl = $prObj.url
        }
    }

    $summary = @()
    $summary += "Implementation completed by automation (Option 3b)."
    $summary += ""
    $summary += "Task summary:"
    $summary += "1. Work item read and analyzed."
    $summary += "2. Branch created: $BranchName"
    $summary += "3. Implementation command executed."
    if (-not $SkipBuild) { $summary += "4. Build executed: SUCCESS" }
    if (-not $SkipTests) { $summary += "5. Tests executed: SUCCESS" }
    $summary += ""
    $summary += "Artifacts:"
    $summary += "- Commit: $commitHash"
    $summary += "- Branch: $BranchName"
    $summary += "- Remote: $remoteUrl"
    if ($prUrl) { $summary += "- PR: $prUrl" }
    $summary += ""
    $summary += "User comment:"
    $summary += $Comment

    $finalComment = ($summary -join [Environment]::NewLine)

    Update-WorkItem -BaseUrl $baseUrl -Project $Project -WorkItemId $WorkItemId -State $NewState -Comment $finalComment -Reason $Reason -Headers $headers

    $verify = Get-WorkItem -BaseUrl $baseUrl -Project $Project -WorkItemId $WorkItemId -Headers $headers

    Write-Host ""
    Write-Host "=== Option 3b Finished ==="
    Write-Host "Final State : $($verify.fields.'System.State')"
    Write-Host "Title       : $($verify.fields.'System.Title')"
    Write-Host "Work Item   : $($verify._links.html.href)"
}
catch {
    Write-Error "Option 3b failed: $($_.Exception.Message)"
    throw
}
