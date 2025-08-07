Param(
  [string]$remote = "origin",
  [string]$branch = ""
)

if (-not $branch -or $branch -eq "") {
  $branch = $(git rev-parse --abbrev-ref HEAD).Trim()
}

Write-Output "Pushing $branch to $remote..."
& git push $remote $branch
if ($LASTEXITCODE -ne 0) {
  Write-Error "git push failed with exit code $LASTEXITCODE"
  exit $LASTEXITCODE
}

# Stop and remove local container if running
$running = $(docker ps -q -f name=^overseer$)
if ($running) {
  Write-Output "Stopping container 'overseer'..."
  docker stop overseer | Out-Null
  docker rm overseer | Out-Null
  Write-Output "Stopped and removed local container 'overseer'."
} else {
  Write-Output "No running 'overseer' container found."
}
