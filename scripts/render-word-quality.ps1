param([ValidateSet('before','after','android')][string]$Phase = 'after', [string]$Filter = '*.docx')
$ErrorActionPreference = 'Stop'
$directory = [IO.Path]::GetFullPath((Join-Path $PSScriptRoot "../.tools/pdf-word-quality/$Phase"))
$word = $null
$results = @()
foreach ($file in Get-ChildItem -LiteralPath $directory -Filter $Filter) {
try {
    # Use a separate invisible instance; never attach to or quit the user's Word session.
    $word = New-Object -ComObject Word.Application
    $word.Visible = $false
    $word.DisplayAlerts = 0
    $word.AutomationSecurity = 3
        $document = $null
        try {
            $document = $word.Documents.Open($file.FullName, $false, $true, $false)
            $document.Repaginate()
            $pages = $document.ComputeStatistics(2)
            $pdf = [IO.Path]::ChangeExtension($file.FullName, '.pdf')
            $document.ExportAsFixedFormat($pdf, 17)
            $results += @{name=$file.Name; pages=$pages; renderer='Microsoft Word'; result='PASS'; sourceSha256=(Get-FileHash -LiteralPath $file.FullName -Algorithm SHA256).Hash.ToLowerInvariant(); pdfSha256=(Get-FileHash -LiteralPath $pdf -Algorithm SHA256).Hash.ToLowerInvariant()}
            Write-Output "$($file.Name): $pages page(s)"
        } finally {
            if ($null -ne $document) { try { $document.Close() } catch { Write-Warning $_ }; [void][Runtime.InteropServices.Marshal]::ReleaseComObject($document) }
        }
} finally {
    if ($null -ne $word) { try { $word.Quit() } catch { Write-Warning $_ }; [void][Runtime.InteropServices.Marshal]::ReleaseComObject($word) }
}
}
$results | ConvertTo-Json -Depth 4 | Set-Content -LiteralPath (Join-Path $directory 'word-render.json') -Encoding UTF8
