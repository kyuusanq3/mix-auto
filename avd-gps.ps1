# avd-gps.ps1 - Inject GPS fixes into the Android Studio emulator
#
# Usage:
#   .\avd-gps.ps1                        # single fix at Government Center (default)
#   .\avd-gps.ps1 10.6765 122.9509       # single fix at custom lat lng
#   .\avd-gps.ps1 -Drive                 # simulate drive: Gov Center to SM City Bacolod
#   .\avd-gps.ps1 -Drive -SpeedKmh 30    # drive at 30 km/h average (default 20)
#   .\avd-gps.ps1 -Drive -OriginLat 10.6765 -OriginLng 122.9509 -DestLat 10.6726 -DestLng 122.9444
#
# Run once per emulator boot. adb emu geo fix is broken on x86_64 AVD images;
# this script uses the test provider API instead.

param(
    [double]$Lat = 10.6765,
    [double]$Lng = 122.9509,
    [switch]$Drive,
    [double]$SpeedKmh = 20.0,
    [double]$OriginLat = 10.6765,
    [double]$OriginLng = 122.9509,
    [double]$DestLat   = 10.6726,
    [double]$DestLng   = 122.9444
)

$dev = "emulator-5554"
$rng = [System.Random]::new()

function adbsh([string]$cmd) {
    & adb -s $dev shell $cmd | Out-Null
}

function Setup-Provider {
    Write-Host "Restarting ADB as root..."
    $rootOut = & adb -s $dev root 2>&1
    Write-Host "  $rootOut"
    Start-Sleep -Seconds 2

    # Android 12 (API 31): LocationManagerService.addTestProvider calls
    #   noteOp(OP_MOCK_LOCATION, callingUid, "android")
    # OP_MOCK_LOCATION defaults to MODE_ERRORED, so we must explicitly allow it
    # by UID number.  Grant for uid 0 (root) and uid 2000 (shell) to cover both.
    Write-Host "Granting mock location permission by uid..."
    & adb -s $dev shell "cmd appops set 0 MOCK_LOCATION allow" 2>&1 | Write-Host
    & adb -s $dev shell "cmd appops set 2000 MOCK_LOCATION allow" 2>&1 | Write-Host

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

# Haversine distance in metres between two lat/lng points
function Get-DistanceM([double]$lat1, [double]$lng1, [double]$lat2, [double]$lng2) {
    $R    = 6371000.0
    $toR  = [Math]::PI / 180.0
    $dLat = ($lat2 - $lat1) * $toR
    $dLng = ($lng2 - $lng1) * $toR
    $a    = [Math]::Sin($dLat/2) * [Math]::Sin($dLat/2) +
            [Math]::Cos($lat1 * $toR) * [Math]::Cos($lat2 * $toR) *
            [Math]::Sin($dLng/2) * [Math]::Sin($dLng/2)
    return $R * 2.0 * [Math]::Atan2([Math]::Sqrt($a), [Math]::Sqrt(1.0 - $a))
}

# Forward bearing in degrees (0-360) from point 1 → point 2
function Get-BearingDeg([double]$lat1, [double]$lng1, [double]$lat2, [double]$lng2) {
    $toR  = [Math]::PI / 180.0
    $dLng = ($lng2 - $lng1) * $toR
    $y    = [Math]::Sin($dLng) * [Math]::Cos($lat2 * $toR)
    $x    = [Math]::Cos($lat1 * $toR) * [Math]::Sin($lat2 * $toR) -
            [Math]::Sin($lat1 * $toR) * [Math]::Cos($lat2 * $toR) * [Math]::Cos($dLng)
    return (([Math]::Atan2($y, $x) * 180.0 / [Math]::PI) + 360.0) % 360.0
}

# Smallest angle between two bearings (0-180)
function Get-BearingDelta([double]$b1, [double]$b2) {
    $d = [Math]::Abs($b2 - $b1) % 360.0
    if ($d -gt 180.0) { $d = 360.0 - $d }
    return $d
}

# Replay an array of @(lat, lng) pairs with realistic urban timing
function Replay-Route([array]$pts) {
    Write-Host "Replaying $($pts.Count) fixes at ~$SpeedKmh km/h with intersection stops"
    Write-Host ""

    for ($i = 0; $i -lt $pts.Count; $i++) {
        $lat = $pts[$i][0]; $lng = $pts[$i][1]

        Send-Fix $lat $lng

        if ($i -ge $pts.Count - 1) { break }

        $nLat = $pts[$i+1][0]; $nLng = $pts[$i+1][1]
        $distM = Get-DistanceM $lat $lng $nLat $nLng

        # Detect turn at current point: compare incoming vs outgoing bearing
        $isTurn = $false
        if ($i -gt 0) {
            $bIn  = Get-BearingDeg $pts[$i-1][0] $pts[$i-1][1] $lat $lng
            $bOut = Get-BearingDeg $lat $lng $nLat $nLng
            $isTurn = (Get-BearingDelta $bIn $bOut) -gt 28
        }

        # Detect upcoming turn within the next 3 points (for approach slowdown)
        $approachingTurn = $false
        for ($k = $i; $k -lt [Math]::Min($i + 3, $pts.Count - 2); $k++) {
            $bK  = Get-BearingDeg $pts[$k][0]   $pts[$k][1]   $pts[$k+1][0] $pts[$k+1][1]
            $bK1 = Get-BearingDeg $pts[$k+1][0] $pts[$k+1][1] $pts[$k+2][0] $pts[$k+2][1]
            if ((Get-BearingDelta $bK $bK1) -gt 28) { $approachingTurn = $true; break }
        }

        # Stop at the turn (traffic light / yield / junction pause)
        if ($isTurn) {
            $stopMs = $rng.Next(1500, 4000)
            Write-Host "  [intersection stop ${stopMs}ms]"
            Start-Sleep -Milliseconds $stopMs
        }

        # Speed: slow on approach/turn, light random variation otherwise
        if ($isTurn -or $approachingTurn) {
            $kmh = $SpeedKmh * ($rng.NextDouble() * 0.2 + 0.25)   # 25-45% of cruise speed
        } else {
            $kmh = $SpeedKmh * ($rng.NextDouble() * 0.3 + 0.85)   # 85-115% of cruise speed
        }
        $kmh = [Math]::Max(3.0, $kmh)

        $delayMs = [int]($distM / ($kmh / 3.6) * 1000.0)
        $delayMs = [Math]::Max(150, [Math]::Min($delayMs, 9000))

        Start-Sleep -Milliseconds $delayMs
    }
}

function Get-OsrmRoute([double]$oLat, [double]$oLng, [double]$dLat, [double]$dLng) {
    $ic   = [System.Globalization.CultureInfo]::InvariantCulture
    $oLngS = $oLng.ToString("G", $ic); $oLatS = $oLat.ToString("G", $ic)
    $dLngS = $dLng.ToString("G", $ic); $dLatS = $dLat.ToString("G", $ic)
    $url  = "https://routing.openstreetmap.de/routed-car/route/v1/driving/$oLngS,$oLatS;$dLngS,$dLatS" + "?overview=full&geometries=geojson"
    $hdrs = @{ "User-Agent" = "MixAutoCarLauncher/1.0" }
    try {
        $r = Invoke-RestMethod -Uri $url -Headers $hdrs -TimeoutSec 10
        $coords = $r.routes[0].geometry.coordinates
        Write-Host "  OSRM: $($coords.Count) road-snapped points fetched"
        return $coords
    } catch {
        Write-Host "  OSRM fetch failed: $_"
        Write-Host "  Falling back to straight-line interpolation"
        return $null
    }
}

function Fallback-Route([double]$oLat, [double]$oLng, [double]$dLat, [double]$dLng, [int]$steps = 30) {
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
        # OSRM GeoJSON coordinates are [lng, lat] — flip to [lat, lng]
        $pts = @()
        foreach ($c in $coords) { $pts += ,@($c[1], $c[0]) }
        Replay-Route $pts
    } else {
        $pts = Fallback-Route $OriginLat $OriginLng $DestLat $DestLng
        Replay-Route $pts
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
