param([string]$Phase = 'browser-after', [switch]$ImportPdf, [string]$Filter = '', [string]$RootDirectory = '.tools/professional-conversion')
$ErrorActionPreference = 'Stop'
$root = [IO.Path]::GetFullPath((Join-Path (Join-Path $PSScriptRoot '..') $RootDirectory))
$env:TEMP = Join-Path $root 'temp'
$env:TMP = $env:TEMP
[void][IO.Directory]::CreateDirectory($env:TEMP)
$directory = Join-Path $root $Phase
[void][IO.Directory]::CreateDirectory($directory)
$inputDirectory = if ($ImportPdf -or $Phase -eq 'word-reference') { Join-Path $root 'sources' } else { $directory }
$filter = if ($ImportPdf) { '*.pdf' } else { '*.docx' }
if ($PSBoundParameters.ContainsKey('Filter')) { $filter = $PSBoundParameters['Filter'] }
$results = @()
if ($PSBoundParameters.ContainsKey('Filter') -and (Test-Path -LiteralPath (Join-Path $directory 'word-render.json'))) {
    $previousRecords = Get-Content -Raw -LiteralPath (Join-Path $directory 'word-render.json') | ConvertFrom-Json
    $results = @($previousRecords | Where-Object { $_.name -notlike $Filter })
}
foreach ($file in Get-ChildItem -LiteralPath $inputDirectory -Filter $filter) {
    $word = $null; $document = $null
    try {
        $word = New-Object -ComObject Word.Application
        $word.Visible = $false; $word.DisplayAlerts = 0; $word.AutomationSecurity = 3
        # Separate hidden instance, no macros, no changes to originals or user's Word session.
        $word.Options.UpdateLinksAtOpen = $false
        $document = $word.Documents.Open($file.FullName, $false, $true, $false)
        if ($null -eq $document) { throw 'Microsoft Word did not return an editable document for this input.' }
        if ($ImportPdf) {
            $docx = Join-Path $directory ($file.BaseName + '.docx')
            $document.SaveAs2($docx, 16)
            $inputHash = (Get-FileHash -LiteralPath $docx -Algorithm SHA256).Hash.ToLowerInvariant()
        } else { $inputHash = (Get-FileHash -LiteralPath $file.FullName -Algorithm SHA256).Hash.ToLowerInvariant() }
        $document.Repaginate()
        $pdf = Join-Path $directory ($file.BaseName + '.pdf')
        $document.ExportAsFixedFormat($pdf, 17)
        $results += @{name=($file.BaseName + '.docx'); pages=$document.ComputeStatistics(2); renderer='Microsoft Word'; sourceSha256=$inputHash; pdfSha256=(Get-FileHash -LiteralPath $pdf -Algorithm SHA256).Hash.ToLowerInvariant()}
        Write-Output "$Phase / $($file.Name): $($document.ComputeStatistics(2)) pages"
    } catch { $results += @{name=$file.Name; error=$_.Exception.Message}; Write-Warning $_ }
    finally {
        if ($null -ne $document) { try { $document.Close(0) } catch { Write-Warning $_ } finally { [void][Runtime.InteropServices.Marshal]::ReleaseComObject($document) } }
        if ($null -ne $word) { try { $word.Quit() } catch { Write-Warning $_ } finally { [void][Runtime.InteropServices.Marshal]::ReleaseComObject($word) } }
    }
}
$results | ConvertTo-Json -Depth 4 | Set-Content -LiteralPath (Join-Path $directory 'word-render.json') -Encoding UTF8
