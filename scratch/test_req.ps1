$prompt = @"
<|im_start|>system
You are Reveila Sovereign AI Agent, an on-device AI assistant running privately on Android.<|im_end|>
<|im_start|>user
Who are you?<|im_end|>
<|im_start|>assistant

"@

$body = @{
    prompt = $prompt
    n_predict = 100
    temperature = 0.7
    stop = @("<|im_end|>")
} | ConvertTo-Json

$stopwatch = [System.Diagnostics.Stopwatch]::StartNew()
$response = Invoke-RestMethod -Uri "http://127.0.0.1:8080/completion" -Method Post -ContentType "application/json" -Body $body
$stopwatch.Stop()

Write-Host "TIME: $($stopwatch.ElapsedMilliseconds) ms"
Write-Host "RESPONSE:"
Write-Host $response.content
