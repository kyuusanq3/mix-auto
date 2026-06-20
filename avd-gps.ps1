# avd-gps.ps1 - Inject GPS fixes into the Android Studio emulator
#
# Usage:
#   .\avd-gps.ps1                       # single fix at Government Center (default)
#   .\avd-gps.ps1 10.6765 122.9509      # single fix at custom lat lng
#   .\avd-gps.ps1 -Drive                # simulate drive: Gov Center to SM City Bacolod (road-snapped via OSRM)
#   .\avd-gps.ps1 -Drive -SpeedMs 500   # faster replay (default 1000ms between fixes)
#   .\avd-gps.ps1 -Drive -OriginLat 10.6765 -OriginLng 122.9509 -DestLat 10.6726 -DestLng 122.9444
#
# Run once per emulator boot. adb emu geo fix is broken on x86_64 AVD images;
# this script uses the test provider API instead.

param(
    [double]$Lat = 10.6765,
    [double]$Lng = 122.9509,
    [switch]$Drive,
    [int]$SpeedMs = 1000,
    [double]$OriginLat = 10.6765,
    [double]$OriginLng = 122.9509,
    [double]$DestLat   = 10.6726,
    [double]$DestLng   = 122.9444
)

$dev = "emulator-5554"

function adbsh([string]$cmd) {
    & adb -s $dev shell $cmd | Out-Null
}

function Setup-Provider {
    Write-Host "Restarting ADB as root..."
    & adb -s $dev root | Out-Null
    Start-Sleep -Seconds 2

    Write-Host "Granting mock location permission..."
    adbsh "appops set shell android:mock_location allow"

    Write-Host "Registering GPS test provider..."
    adbsh "cmd location providers add-test-provider gps --supportsSpeed --supportsBearing --supportsAltitude"
    adbsh "cmd location providers set-test-provider-enabled gps true"
    Write-Host "Provider ready."
    Write-Host ""
}

function Send-Fix([double]$FixLat, [double]$FixLng) {
    $ic = [System.Globalization.CultureInfo]::InvariantCulture
    $latS = $FixLat.ToString("G", $ic); $lngS = $FixLng.ToString("G", $ic)
    adbsh "cmd location providers set-test-provider-location gps --location $latS,$lngS --accuracy 5"
    Write-Host "  Fix: $latS, $lngS"
}

function Get-OsrmRoute([double]$oLat, [double]$oLng, [double]$dLat, [double]$dLng) {
    $ic = [System.Globalization.CultureInfo]::InvariantCulture
    $oLngS = $oLng.ToString("G", $ic); $oLatS = $oLat.ToString("G", $ic)
    $dLngS = $dLng.ToString("G", $ic); $dLatS = $dLat.ToString("G", $ic)
    $url = "https://routing.openstreetmap.de/routed-car/route/v1/driving/$oLngS,$oLatS;$dLngS,$dLatS" + "?overview=full&geometries=geojson"
    $headers = @{ "User-Agent" = "MixAutoCarLauncher/1.0" }
    try {
        $r = Invoke-RestMethod -Uri $url -Headers $headers -TimeoutSec 10
        $coords = $r.routes[0].geometry.coordinates
        Write-Host "  OSRM: $($coords.Count) road-snapped points fetched"
        return $coords
    } catch {
        Write-Host "  OSRM fetch failed: $_"
        Write-Host "  Falling back to straight-line interpolation"
        return $null
    }
}

function Fallback-Route([double]$oLat, [double]$oLng, [double]$dLat, [double]$dLng, [int]$steps = 14) {
    $pts = @()
    for ($i = 0; $i -le $steps; $i++) {
        $t = $i / $steps
        $pts += ,@(($oLat + ($dLat - $oLat) * $t), ($oLng + ($dLng - $oLng) * $t))
    }
    return $pts
}

Setup-Provider

if ($Drive) {
    Write-Host "Fetching road-snapped route from OSRM..."
    $coords = Get-OsrmRoute $OriginLat $OriginLng $DestLat $DestLng
    Write-Host ""

    if ($coords) {
        Write-Host "Replaying $($coords.Count) fixes along road geometry (${SpeedMs}ms apart)"
        Write-Host ""
        foreach ($c in $coords) {
            # OSRM GeoJSON is [lng, lat]
            Send-Fix $c[1] $c[0]
            Start-Sleep -Milliseconds $SpeedMs
        }
    } else {
        $pts = Fallback-Route $OriginLat $OriginLng $DestLat $DestLng
        Write-Host "Replaying $($pts.Count) interpolated fixes (${SpeedMs}ms apart)"
        Write-Host ""
        foreach ($p in $pts) {
            Send-Fix $p[0] $p[1]
            Start-Sleep -Milliseconds $SpeedMs
        }
    }

    Write-Host ""
    Write-Host "Drive complete."
} else {
    Write-Host "Sending single fix: $Lat, $Lng"
    Send-Fix $Lat $Lng
    Write-Host ""
    Write-Host "To simulate driving run: .\avd-gps.ps1 -Drive"
}

Write-Host "To send a custom fix run: .\avd-gps.ps1 LAT LNG"
