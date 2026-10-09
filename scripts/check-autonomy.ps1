$ErrorActionPreference = 'Stop'
$projectRoot = [IO.Path]::GetFullPath((Split-Path -Parent $PSScriptRoot))
$frontendRoot = Join-Path $projectRoot 'frontend'
$violations = [Collections.Generic.List[string]]::new()

function Test-InternalPath {
    param([string]$BaseDirectory, [string]$Reference, [string]$Source)
    $resolved = [IO.Path]::GetFullPath((Join-Path $BaseDirectory $Reference))
    $rootPrefix = $projectRoot + [IO.Path]::DirectorySeparatorChar
    if (!$resolved.StartsWith($rootPrefix, [StringComparison]::OrdinalIgnoreCase)) {
        $violations.Add("$Source : chemin hors de Planning ($Reference).")
    }
}

$sourceFiles = @(
    Get-ChildItem -LiteralPath (Join-Path $frontendRoot 'src') -Recurse -File -Include '*.ts', '*.tsx'
    Get-ChildItem -LiteralPath (Join-Path $projectRoot 'backend/src') -Recurse -File -Include '*.java', '*.sql', '*.yml'
)
foreach ($source in $sourceFiles) {
    $content = Get-Content -LiteralPath $source.FullName -Raw -Encoding UTF8
    if ($source.Extension -in @('.ts', '.tsx')) {
        $imports = [regex]::Matches($content, '(?:\bfrom\s*|\bimport\s*\(?\s*)["''](?<path>\.[^"'']+)["'']')
        foreach ($import in $imports) {
            Test-InternalPath -BaseDirectory $source.DirectoryName -Reference $import.Groups['path'].Value -Source $source.FullName
        }
    }
    if ($content -match '(?m)^\s*import\s+(?:static\s+)?com\.progression\.') {
        $violations.Add("$($source.FullName) : import Java de Progression.")
    }
    if ($source.Extension -eq '.sql' -and $content -match '(?i)\b(?:REFERENCES|FROM|JOIN|INTO|UPDATE)\s+"?(?:public|progression)"?\.') {
        $violations.Add("$($source.FullName) : accès SQL à un schéma externe.")
    }
}

[xml]$pom = Get-Content -LiteralPath (Join-Path $projectRoot 'backend/pom.xml') -Raw -Encoding UTF8
foreach ($node in $pom.SelectNodes('//*[local-name()="relativePath" or local-name()="systemPath" or local-name()="module"]')) {
    if ($node.InnerText.Trim()) {
        Test-InternalPath -BaseDirectory (Join-Path $projectRoot 'backend') -Reference $node.InnerText.Trim() -Source 'backend/pom.xml'
    }
}
$package = Get-Content -LiteralPath (Join-Path $frontendRoot 'package.json') -Raw -Encoding UTF8 | ConvertFrom-Json
foreach ($dependencies in @($package.dependencies, $package.devDependencies)) {
    foreach ($dependency in $dependencies.PSObject.Properties) {
        if ($dependency.Value -match '^(?:file|link):(?<path>.+)$') {
            Test-InternalPath -BaseDirectory $frontendRoot -Reference $Matches['path'] -Source 'frontend/package.json'
        }
    }
}

$compose = Get-Content -LiteralPath (Join-Path $projectRoot 'docker-compose.yml') -Raw -Encoding UTF8
if ($compose -match '(?i)host\.docker\.internal|\.\.[/\\]|external:\s*true|container_name:') {
    $violations.Add('docker-compose.yml : dépendance hôte, ressource externe ou nom de conteneur global.')
}
foreach ($context in [regex]::Matches($compose, '(?m)^\s*context:\s*["'']?(?<path>[^\s"'']+)')) {
    Test-InternalPath -BaseDirectory $projectRoot -Reference $context.Groups['path'].Value -Source 'docker-compose.yml'
}
foreach ($dockerfile in @('backend/Dockerfile', 'frontend/Dockerfile')) {
    $content = Get-Content -LiteralPath (Join-Path $projectRoot $dockerfile) -Raw -Encoding UTF8
    if ($content -match '(?im)^\s*(?:COPY|ADD)\s+.*\.\.[/\\]') { $violations.Add("$dockerfile : copie hors du contexte de build.") }
}

if ($violations.Count -gt 0) { throw ($violations -join [Environment]::NewLine) }
Write-Host "Audit d'autonomie réussi : imports, manifestes, SQL et configuration runtime."
