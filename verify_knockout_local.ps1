# Verificación sobre el stack local mundia-prod (:8086), pool 1, usuario admin 1
$ErrorActionPreference = 'Stop'
$base = 'http://localhost:8086/api'
$tok = Get-Content "$env:TEMP\mundia_local_jwt.txt"
$H = @{ Authorization = "Bearer $tok" }
$poolId = 1

function Section($t) { Write-Output "`n===== $t =====" }

Section 'Dashboard tras V15'
$d = Invoke-RestMethod -Uri "$base/dashboard/$poolId" -Headers $H
$byStage = $d.matches | Group-Object stage | ForEach-Object { "$($_.Name)=$($_.Count)" }
Write-Output "matches por stage: $($byStage -join ', ')"
$d.bracketRounds | Where-Object { $_.name -notlike 'Group*' } | ForEach-Object {
  Write-Output ("ronda {0}: {1} partidos" -f $_.name, $_.matches.Count)
}

Section 'Pred sobre placeholder R32 (admin)'
$r32 = ($d.bracketRounds | Where-Object name -eq 'Round of 32').matches[0]
$marked = $r32.matchId
Invoke-RestMethod -Method Post -Uri "$base/predictions/match" -Headers $H -ContentType 'application/json' -Body (@{
  poolId = $poolId; matchId = $marked; homeGoals = 7; awayGoals = 7
} | ConvertTo-Json) | Out-Null
Write-Output "pred 7-7 guardada en matchId=$marked"

Section 'Simulacion completa'
Invoke-RestMethod -Method Post -Uri "$base/admin/sim/users/$poolId" -Headers $H -ContentType 'application/json' -Body '{"count":3}' | Out-Null
Invoke-RestMethod -Method Post -Uri "$base/admin/sim/full/$poolId" -Headers $H | Out-Null
Write-Output 'sim full ok'

Section 'Tras simular'
$d2 = Invoke-RestMethod -Uri "$base/dashboard/$poolId" -Headers $H
$d2.bracketRounds | Where-Object { $_.name -notlike 'Group*' } | ForEach-Object {
  $done = @($_.matches | Where-Object done).Count
  Write-Output ("ronda {0}: {1} partidos ({2} con resultado)" -f $_.name, $_.matches.Count, $done)
}
$m2 = ($d2.bracketRounds | Where-Object name -eq 'Round of 32').matches | Where-Object matchId -eq $marked
Write-Output ("matchId={0}: home={1} away={2} pred={3} real={4}" -f $marked, $m2.home, $m2.away, $m2.pred, $m2.real)
$knock = @($d2.matches | Where-Object stage -ne 'GROUP_STAGE')
Write-Output ("Partidos: {0} eliminatorias, {1} con resultado" -f $knock.Count, @($knock | Where-Object { $_.real -ne 'Pend.' }).Count)

Section 'Reset'
Invoke-RestMethod -Method Delete -Uri "$base/admin/sim/reset/$poolId" -Headers $H | Out-Null
$d3 = Invoke-RestMethod -Uri "$base/dashboard/$poolId" -Headers $H
$d3.bracketRounds | Where-Object { $_.name -notlike 'Group*' } | ForEach-Object {
  $withResult = @($_.matches | Where-Object { $_.real -ne 'Pend.' }).Count
  $withTeams  = @($_.matches | Where-Object { $_.home -ne 'Pendiente' }).Count
  Write-Output ("ronda {0}: {1} partidos, {2} resultado residual, {3} con equipos" -f $_.name, $_.matches.Count, $withResult, $withTeams)
}
Write-Output ("grupos con resultado residual: " + @($d3.matches | Where-Object { $_.stage -eq 'GROUP_STAGE' -and $_.real -ne 'Pend.' }).Count)
Write-Output ("eliminatorias con resultado residual: " + @($d3.matches | Where-Object { $_.stage -ne 'GROUP_STAGE' -and $_.real -ne 'Pend.' }).Count)
Write-Output ("usuarios sim restantes: " + (docker exec mundia-database mariadb -N -u mundia -pmundia_password mundia -e "SELECT COUNT(*) FROM users WHERE is_sim=1"))

Section 'FIN'
