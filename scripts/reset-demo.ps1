$ErrorActionPreference = 'Stop'
$session = Invoke-RestMethod -Uri http://localhost:8081/api/auth/login -Method Post -ContentType application/json -Body '{"email":"demo@ankidsa.local","password":"DemoRecall!2026"}'
Invoke-RestMethod -Uri http://localhost:8081/api/demo/reset -Method Post -Headers @{ Authorization = "Bearer $($session.token)" }
Write-Output 'The local sample account has been reset. Refresh the app and extension.'
