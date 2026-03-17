Write-Host "Registrando usuario temporario para obter hash bcrypt..." -ForegroundColor Cyan

$tempEmail = "admin-temp-$(Get-Random)@leadflow.com"

$registerBody = @{
    name = "Temp Admin"
    email = $tempEmail
    password = "Admin@123"
    confirmPassword = "Admin@123"
} | ConvertTo-Json

Write-Host "Email temporario: $tempEmail" -ForegroundColor Gray
Write-Host "Senha: Admin@123" -ForegroundColor Gray

try {
    $response = Invoke-WebRequest -Uri "http://localhost:8081/auth/register" `
        -Method POST `
        -ContentType "application/json" `
        -Body $registerBody `
        -TimeoutSec 10 `
        -ErrorAction Stop

    $data = $response.Content | ConvertFrom-Json
    Write-Host "Usuario registrado: $($data.user.email)" -ForegroundColor Green
    Write-Host ""
    Write-Host "Agora procurando o hash no banco..." -ForegroundColor Cyan
    
    # Procurar o hash no banco
    $env:PGPASSWORD = "venusia"
    $hashResult = psql postgresql://postgres:venusia@localhost:2411/leadflow_test `
        -c "SELECT password FROM public.users WHERE email = '$tempEmail' LIMIT 1;" `
        2>&1
    
    Write-Host "Hash encontrado:" -ForegroundColor Green
    Write-Host $hashResult -ForegroundColor Yellow
    
} catch {
    Write-Host "Erro: $($_.Exception.Message)" -ForegroundColor Red
    if ($_.Exception.Response) {
        $reader = New-Object System.IO.StreamReader($_.Exception.Response.GetResponseStream())
        Write-Host $reader.ReadToEnd() -ForegroundColor Yellow
    }
}
