$source = $PSScriptRoot
$dest = "C:\VG"

Write-Host "Moving project source from:"
Write-Host "  $source"
Write-Host "to:"
Write-Host "  $dest"
Write-Host ""
Write-Host "Excluding: node_modules, .git, .gradle, .idea, build folders"
Write-Host "------------------------------------------------------------"

# Create destination if it doesn't exist
if (-not (Test-Path $dest)) {
    New-Item -ItemType Directory -Force -Path $dest | Out-Null
}

# Use Robocopy for robust copying with exclusions
# /E :: Copy subdirectories, including Empty ones.
# /XD :: eXclude Directories matching given names/paths.
# /XF :: eXclude Files matching given names/paths.
# /R:0 /W:0 :: No retries, no wait (fail fast on locked files).
robocopy "$source" "$dest" /E /XD "node_modules" ".git" ".gradle" ".idea" "build" "app\build" ".cxx" /XF "*.iml" "*.log" /R:0 /W:0

Write-Host "------------------------------------------------------------"
Write-Host "Move Complete!"
Write-Host ""
Write-Host "NEXT STEPS:"
Write-Host "1. Open '$dest' in VS Code."
Write-Host "2. Run 'npm install' to restore dependencies."
Write-Host "3. Run 'cd android' and './gradlew clean'."
Write-Host "4. Try running the app."
Write-Host ""
Read-Host -Prompt "Press Enter to exit"
