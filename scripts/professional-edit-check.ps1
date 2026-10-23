$ErrorActionPreference = 'Stop'
$root = [IO.Path]::GetFullPath((Join-Path $PSScriptRoot '../.tools/professional-conversion'))
$env:TEMP = Join-Path $root 'temp'; $env:TMP = $env:TEMP
$directory = Join-Path $root 'editability'
[void][IO.Directory]::CreateDirectory($directory)
$word = $null; $document = $null
try {
    $copy = Join-Path $directory 'merged-table-edited.docx'
    Copy-Item -LiteralPath (Join-Path $root 'browser-after/merged-table.docx') -Destination $copy -Force
    $word = New-Object -ComObject Word.Application
    $word.Visible = $false; $word.DisplayAlerts = 0; $word.AutomationSecurity = 3
    $word.Options.UpdateLinksAtOpen = $false
    $document = $word.Documents.Open($copy, $false, $false, $false)
    if ($null -eq $document) { throw 'Word did not open the generated editable document.' }
    if ($document.Tables.Count -ne 1) { throw 'Expected one actual Word table.' }
    $range = $document.Tables.Item(1).Cell(2,3).Range.Duplicate
    $range.End = $range.End - 2
    $range.Text = 'Edited cell line' + [char]11 + 'Second cell line'
    $document.Save()
    $document.ExportAsFixedFormat((Join-Path $directory 'merged-table-edited.pdf'), 17)
    @{result='PASS';renderer='Microsoft Word';tables=$document.Tables.Count;cells=$document.Tables.Item(1).Range.Cells.Count;editedTextFound=$document.Content.Text.Contains('Edited cell line');scope='Edited an actual table cell in Word, saved a separate DOCX and rendered it. Originals unchanged; extensive paragraph reflow is a separate limitation.'} | ConvertTo-Json | Set-Content -LiteralPath (Join-Path $directory 'verification.json') -Encoding UTF8
    Write-Output 'Actual Word table-cell edit saved and rendered.'
} finally {
    if ($null -ne $document) { try { $document.Close(0) } catch { Write-Warning $_ } finally { [void][Runtime.InteropServices.Marshal]::ReleaseComObject($document) } }
    if ($null -ne $word) { try { $word.Quit() } catch { Write-Warning $_ } finally { [void][Runtime.InteropServices.Marshal]::ReleaseComObject($word) } }
}
