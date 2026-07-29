$ErrorActionPreference = 'Stop'

[Console]::OutputEncoding = [System.Text.Encoding]::UTF8

[PSCustomObject]@{
    repository = 'oryx-labs/oryxos'
    marker = 'GITHUB_SCRIPT_MARKER_42'
    openIssues = 7
} | ConvertTo-Json -Compress
