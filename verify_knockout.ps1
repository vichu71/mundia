# Verificación E2E del modelo canónico de eliminatorias (entorno desechable :8081)
$ErrorActionPreference = 'Stop'
$base = 'http://localhost:8081/api'

function Section($t) { Write-Output "`n===== $t =====" }

# 1. Registrar usuario de prueba (queda como ADMIN al crear su porra)
Section 'Registro + porra'
$reg = Invoke-RestMethod -Method Post -Uri "$base/auth/register" -ContentType 'application/json' -Body (@{
  email = 'test_knockout@mundia.test'; password = 'test123456'; displayName = 'Tester Knockout'
} | ConvertTo-Json)
$tok = $reg.token
$H = @{ Authorization = "Bearer $tok" }
$pool = Invoke-RestMethod -Method Post -Uri "$base/pools" -Headers $H -ContentType 'application/json' -Body (@{
  name = ''; description = 'Porra test'; entryFeeCents = 1000; currency = 'EUR'
} | ConvertTo-Json)
$poolId = $pool.poolId
if (-not $poolId) { $poolId = $pool.id }
Write-Output "poolId=$poolId"

# 2. Sync WC26 (equipos + fixtures + grupos reales)
Section 'Sync WC26'
try {
  Invoke-RestMethod -Method Post -Uri "$base/admin/sports-sync/wc26/all" -Headers $H | Out-Null
  Write-Output 'sync ok'
} catch { Write-Output "sync fallo: $($_.Exception.Message)" }

# 3. Dashboard inicial: partidos por stage y rondas del cuadro
Section 'Dashboard inicial'
$d = Invoke-RestMethod -Uri "$base/dashboard/$poolId" -Headers $H
$byStage = $d.matches | Group-Object stage | ForEach-Object { "$($_.Name)=$($_.Count)" }
Write-Output "matches por stage: $($byStage -join ', ')"
$d.bracketRounds | Where-Object { $_.name -notlike 'Group*' } | ForEach-Object {
  Write-Output ("ronda {0}: {1} partidos" -f $_.name, $_.matches.Count)
}

# 4. Predicción de usuario real sobre un placeholder de R32 (debe sobrevivir al fill)
Section 'Pred sobre placeholder R32'
$r32 = ($d.bracketRounds | Where-Object name -eq 'Round of 32').matches[0]
$marked = $r32.matchId
Invoke-RestMethod -Method Post -Uri "$base/predictions/match" -Headers $H -ContentType 'application/json' -Body (@{
  poolId = $poolId; matchId = $marked; homeGoals = 7; awayGoals = 7
} | ConvertTo-Json) | Out-Null
Write-Output "pred 7-7 guardada en matchId=$marked"

# 5. Usuarios sim + torneo completo
Section 'Simulacion completa'
Invoke-RestMethod -Method Post -Uri "$base/admin/sim/users/$poolId" -Headers $H -ContentType 'application/json' -Body '{"count":3}' | Out-Null
$full = Invoke-RestMethod -Method Post -Uri "$base/admin/sim/full/$poolId" -Headers $H
Write-Output $full

# 6. Estado tras simular: sin duplicados, mismas filas (id estable)
Section 'Dashboard tras simular'
$d2 = Invoke-RestMethod -Uri "$base/dashboard/$poolId" -Headers $H
$d2.bracketRounds | Where-Object { $_.name -notlike 'Group*' } | ForEach-Object {
  $done = ($_.matches | Where-Object done).Count
  Write-Output ("ronda {0}: {1} partidos ({2} con resultado)" -f $_.name, $_.matches.Count, $done)
}
$m2 = ($d2.bracketRounds | Where-Object name -eq 'Round of 32').matches | Where-Object matchId -eq $marked
Write-Output ("matchId={0} sigue en R32: home={1} away={2} pred={3} real={4}" -f $marked, $m2.home, $m2.away, $m2.pred, $m2.real)
$knock = $d2.matches | Where-Object stage -ne 'GROUP_STAGE'
Write-Output ("pestana Partidos: {0} partidos de eliminatoria, {1} con resultado real" -f $knock.Count, ($knock | Where-Object { $_.real -ne 'Pend.' }).Count)

# 7. Reset y comprobacion de residuos
Section 'Reset simulacion'
Invoke-RestMethod -Method Delete -Uri "$base/admin/sim/reset/$poolId" -Headers $H | Out-Null
$d3 = Invoke-RestMethod -Uri "$base/dashboard/$poolId" -Headers $H
$d3.bracketRounds | Where-Object { $_.name -notlike 'Group*' } | ForEach-Object {
  $withResult = ($_.matches | Where-Object { $_.real -ne 'Pend.' }).Count
  $withTeams  = ($_.matches | Where-Object { $_.home -ne 'Pendiente' }).Count
  Write-Output ("ronda {0}: {1} partidos, {2} con resultado residual, {3} con equipos" -f $_.name, $_.matches.Count, $withResult, $withTeams)
}
$resGroup = ($d3.matches | Where-Object { $_.stage -eq 'GROUP_STAGE' -and $_.real -ne 'Pend.' }).Count
Write-Output "grupos con resultado residual: $resGroup"
$resKnock = ($d3.matches | Where-Object { $_.stage -ne 'GROUP_STAGE' -and $_.real -ne 'Pend.' }).Count
Write-Output "eliminatorias con resultado residual: $resKnock"

Section 'FIN'
