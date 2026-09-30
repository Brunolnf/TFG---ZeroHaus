<#
.SYNOPSIS
    Compila la versión release de ZeroHaus y la envía a testers con
    Firebase App Distribution (les llega un email con el enlace de descarga).

.DESCRIPTION
    Pensado para repartir la app al tribunal o a probadores sin pasar por
    Google Play. Requiere la CLI de Firebase con sesión iniciada
    (firebase login) y el keystore de release configurado.

    Limitación: la app instalada así no viene de Google Play, así que App Check
    (Play Integrity) no la reconoce y las funciones protegidas (consejos con IA,
    leer la factura, eliminar la cuenta, suscripciones) fallarán en esos móviles.
    Para probarlas hay que usar la pista de pruebas internas de Google Play.

.EXAMPLE
    .\herramientas\distribuir.ps1 -Testers "ana@gmail.com,luis@gmail.com" -Notas "Versión para el tribunal"
#>
param(
    [Parameter(Mandatory = $true)][string]$Testers,
    [string]$Notas = "Versión de prueba de ZeroHaus"
)

$ErrorActionPreference = "Stop"
$appId = "1:140712303137:android:bdf070ae95ad00512d6160"
$raiz = Split-Path -Parent $PSScriptRoot

Push-Location $raiz
try {
    & .\gradlew.bat assembleRelease
    if ($LASTEXITCODE -ne 0) { throw "La compilación ha fallado." }
    $apk = "app\build\outputs\apk\release\app-release.apk"
    firebase appdistribution:distribute $apk --app $appId --testers $Testers --release-notes $Notas
    if ($LASTEXITCODE -ne 0) { throw "No se pudo subir a App Distribution." }
}
finally {
    Pop-Location
}
