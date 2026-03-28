[CmdletBinding()]
param(
    [string]$Tag = "latest",
    [string]$EnvFile,
    [switch]$IncludeEnvs
)

Set-StrictMode -Version Latest
$ErrorActionPreference = "Stop"

function Write-Step {
    param(
        [int]$Index,
        [int]$Total,
        [string]$Message
    )

    Write-Host "==> [$Index/$Total] $Message"
}

function Resolve-CommandPath {
    param(
        [Parameter(Mandatory = $true)]
        [string[]]$Candidates
    )

    foreach ($candidate in $Candidates) {
        $command = Get-Command $candidate -ErrorAction SilentlyContinue | Select-Object -First 1
        if ($null -ne $command) {
            return $command.Source
        }

        if (Test-Path -LiteralPath $candidate) {
            return (Resolve-Path -LiteralPath $candidate).Path
        }
    }

    throw "Missing required command. Tried: $($Candidates -join ', ')"
}

function Invoke-NativeCommand {
    param(
        [Parameter(Mandatory = $true)]
        [string]$FilePath,
        [string[]]$Arguments = @(),
        [string]$WorkingDirectory = (Get-Location).Path,
        [string]$InputText
    )

    Push-Location $WorkingDirectory
    try {
        if ($PSBoundParameters.ContainsKey("InputText")) {
            $InputText | & $FilePath @Arguments
        }
        else {
            & $FilePath @Arguments
        }

        if ($LASTEXITCODE -ne 0) {
            throw "Command failed with exit code ${LASTEXITCODE}: $FilePath"
        }
    }
    finally {
        Pop-Location
    }
}

function Read-KeyValueFile {
    param(
        [Parameter(Mandatory = $true)]
        [string]$Path
    )

    if (-not (Test-Path -LiteralPath $Path)) {
        throw "Config file not found: $Path`nCopy deploy\env.example to deploy\env.local and fill in the values."
    }

    $values = [ordered]@{}

    foreach ($line in Get-Content -LiteralPath $Path) {
        $trimmed = $line.Trim()
        if (-not $trimmed -or $trimmed.StartsWith("#")) {
            continue
        }

        $parts = $trimmed -split "=", 2
        if ($parts.Count -ne 2) {
            continue
        }

        $key = $parts[0].Trim()
        $value = $parts[1].Trim()

        if (
            ($value.StartsWith('"') -and $value.EndsWith('"')) -or
            ($value.StartsWith("'") -and $value.EndsWith("'"))
        ) {
            $value = $value.Substring(1, $value.Length - 2)
        }

        $values[$key] = $value
    }

    return $values
}

function Get-ConfigValue {
    param(
        [Parameter(Mandatory = $true)]
        [System.Collections.IDictionary]$Config,
        [Parameter(Mandatory = $true)]
        [string[]]$Keys
    )

    foreach ($key in $Keys) {
        if ($Config.Contains($key) -and -not [string]::IsNullOrWhiteSpace([string]$Config[$key])) {
            return [string]$Config[$key]
        }
    }

    foreach ($key in $Keys) {
        $envItem = Get-Item -Path "Env:$key" -ErrorAction SilentlyContinue
        if ($null -ne $envItem -and -not [string]::IsNullOrWhiteSpace($envItem.Value)) {
            return $envItem.Value
        }
    }

    return $null
}

function Require-ConfigValue {
    param(
        [Parameter(Mandatory = $true)]
        [System.Collections.IDictionary]$Config,
        [Parameter(Mandatory = $true)]
        [string]$Name,
        [Parameter(Mandatory = $true)]
        [string[]]$Keys
    )

    $value = Get-ConfigValue -Config $Config -Keys $Keys
    if ([string]::IsNullOrWhiteSpace($value)) {
        throw "Missing required config value for $Name. Checked: $($Keys -join ', ')"
    }

    return $value
}

function New-SaeEnvJson {
    param(
        [Parameter(Mandatory = $true)]
        [System.Collections.IDictionary]$Config
    )

    $reservedKeys = [System.Collections.Generic.HashSet[string]]::new([System.StringComparer]::OrdinalIgnoreCase)
    foreach ($key in @(
        "APP_ID",
        "SAE_APP_ID",
        "REGISTRY",
        "ACR_REGISTRY",
        "REGISTRY_USERNAME",
        "ACR_USERNAME",
        "REGISTRY_PASSWORD",
        "ACR_PASSWORD",
        "REMOTE_IMAGE"
    )) {
        [void]$reservedKeys.Add($key)
    }

    $envList = foreach ($entry in $Config.GetEnumerator()) {
        if (-not $reservedKeys.Contains([string]$entry.Key)) {
            [ordered]@{
                name  = [string]$entry.Key
                value = [string]$entry.Value
            }
        }
    }

    return @($envList) | ConvertTo-Json -Compress
}

$scriptDir = $PSScriptRoot
$projectRoot = Split-Path -Parent $scriptDir

if ([string]::IsNullOrWhiteSpace($EnvFile)) {
    $EnvFile = Join-Path $scriptDir "env.local"
}

$config = Read-KeyValueFile -Path $EnvFile

$appId = Require-ConfigValue -Config $config -Name "SAE app id" -Keys @("SAE_APP_ID", "APP_ID")
$registry = Require-ConfigValue -Config $config -Name "ACR registry" -Keys @("ACR_REGISTRY", "REGISTRY")
$registryUsername = Require-ConfigValue -Config $config -Name "ACR username" -Keys @("ACR_USERNAME", "REGISTRY_USERNAME")
$registryPassword = Require-ConfigValue -Config $config -Name "ACR password" -Keys @("ACR_PASSWORD", "REGISTRY_PASSWORD")
$remoteImage = Require-ConfigValue -Config $config -Name "remote image" -Keys @("REMOTE_IMAGE")
$region = Get-ConfigValue -Config $config -Keys @("ALIYUN_REGION_ID", "ALIYUN_REGION", "SAE_REGION")
if ([string]::IsNullOrWhiteSpace($region)) {
    $region = "us-west-1"
}
$aliyunAccessKeyId = Get-ConfigValue -Config $config -Keys @("ALIYUN_ACCESS_KEY_ID", "ALIBABA_CLOUD_ACCESS_KEY_ID", "OSS_ACCESS_KEY_ID")
$aliyunAccessKeySecret = Get-ConfigValue -Config $config -Keys @("ALIYUN_ACCESS_KEY_SECRET", "ALIBABA_CLOUD_ACCESS_KEY_SECRET", "OSS_ACCESS_KEY_SECRET")

$localImage = "playforge:$Tag"
$dockerCommand = Resolve-CommandPath -Candidates @("docker")
$aliyunCommand = Resolve-CommandPath -Candidates @("aliyun", "aliyun.exe", "aliyun.cmd", "D:\aliyun-cli\aliyun.exe")

Write-Step -Index 1 -Total 3 -Message "Building Docker image (frontend + backend)"
Invoke-NativeCommand -FilePath $dockerCommand -Arguments @("build", "--platform", "linux/amd64", "-t", $localImage, $projectRoot) -WorkingDirectory $projectRoot

Write-Step -Index 2 -Total 3 -Message "Logging in to ACR and pushing image"
Invoke-NativeCommand -FilePath $dockerCommand -Arguments @("login", "--username=$registryUsername", "--password-stdin", $registry) -WorkingDirectory $projectRoot -InputText $registryPassword
Invoke-NativeCommand -FilePath $dockerCommand -Arguments @("tag", $localImage, "${remoteImage}:$Tag") -WorkingDirectory $projectRoot
Invoke-NativeCommand -FilePath $dockerCommand -Arguments @("push", "${remoteImage}:$Tag") -WorkingDirectory $projectRoot

Write-Step -Index 3 -Total 3 -Message "Deploying to SAE"
$previousRegion = $env:ALIBABA_CLOUD_REGION_ID
$previousAccessKeyId = $env:ALIBABA_CLOUD_ACCESS_KEY_ID
$previousAccessKeySecret = $env:ALIBABA_CLOUD_ACCESS_KEY_SECRET

try {
    $env:ALIBABA_CLOUD_REGION_ID = $region
    if (-not [string]::IsNullOrWhiteSpace($aliyunAccessKeyId)) {
        $env:ALIBABA_CLOUD_ACCESS_KEY_ID = $aliyunAccessKeyId
    }
    if (-not [string]::IsNullOrWhiteSpace($aliyunAccessKeySecret)) {
        $env:ALIBABA_CLOUD_ACCESS_KEY_SECRET = $aliyunAccessKeySecret
    }

    $deployArguments = @(
        "--region",
        $region,
        "sae",
        "DeployApplication",
        "--AppId",
        $appId,
        "--ImageUrl",
        "${remoteImage}:$Tag"
    )

    if ($IncludeEnvs.IsPresent) {
        $saeEnvs = New-SaeEnvJson -Config $config
        $deployArguments += "--Envs=$saeEnvs"
    }

    Invoke-NativeCommand -FilePath $aliyunCommand -Arguments $deployArguments -WorkingDirectory $projectRoot
}
finally {
    if ($null -eq $previousRegion) {
        Remove-Item Env:ALIBABA_CLOUD_REGION_ID -ErrorAction SilentlyContinue
    }
    else {
        $env:ALIBABA_CLOUD_REGION_ID = $previousRegion
    }

    if ($null -eq $previousAccessKeyId) {
        Remove-Item Env:ALIBABA_CLOUD_ACCESS_KEY_ID -ErrorAction SilentlyContinue
    }
    else {
        $env:ALIBABA_CLOUD_ACCESS_KEY_ID = $previousAccessKeyId
    }

    if ($null -eq $previousAccessKeySecret) {
        Remove-Item Env:ALIBABA_CLOUD_ACCESS_KEY_SECRET -ErrorAction SilentlyContinue
    }
    else {
        $env:ALIBABA_CLOUD_ACCESS_KEY_SECRET = $previousAccessKeySecret
    }
}

Write-Host ""
Write-Host "==> All done! Check SAE console for deployment status."
