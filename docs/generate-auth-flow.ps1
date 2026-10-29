Add-Type -AssemblyName System.Drawing
$bitmap = [System.Drawing.Bitmap]::new(1600, 1420)
$g = [System.Drawing.Graphics]::FromImage($bitmap)
$g.SmoothingMode = 'AntiAlias'
$g.TextRenderingHint = 'AntiAliasGridFit'
$g.Clear([System.Drawing.ColorTranslator]::FromHtml('#f3f6fb'))
function Brush($hex) { [System.Drawing.SolidBrush]::new([System.Drawing.ColorTranslator]::FromHtml($hex)) }
function Text($value, $x, $y, $size=19, $color='#253552', $bold=$false) {
    $style = if ($bold) { [System.Drawing.FontStyle]::Bold } else { [System.Drawing.FontStyle]::Regular }
    $font = [System.Drawing.Font]::new('Segoe UI', $size, $style, [System.Drawing.GraphicsUnit]::Pixel)
    $brush = Brush $color
    $g.DrawString($value, $font, $brush, [single]$x, [single]$y)
    $font.Dispose(); $brush.Dispose()
}
function Rect($x, $y, $w, $h, $color) {
    $brush = Brush $color
    $g.FillRectangle($brush, $x, $y, $w, $h)
    $brush.Dispose()
}
function Arrow($x1, $y1, $x2, $y2, $color='#7c8da8') {
    $pen = [System.Drawing.Pen]::new([System.Drawing.ColorTranslator]::FromHtml($color), 3)
    $pen.CustomEndCap = [System.Drawing.Drawing2D.AdjustableArrowCap]::new(5, 6)
    $g.DrawLine($pen, $x1, $y1, $x2, $y2)
    $pen.Dispose()
}
function Box($x, $y, $title, $lines, $accent) {
    Rect $x $y 330 112 '#ffffff'
    Rect $x $y 5 112 $accent
    Text $title ($x+18) ($y+15) 21 '#142540' $true
    Text $lines ($x+18) ($y+49) 18
}
function Row($y, $number, $title, $subtitle, $accent, $titles, $lines, $note) {
    Rect 40 $y 1520 252 '#e8eef7'
    Rect 40 $y 56 42 $accent
    Text $number 57 ($y+7) 23 '#ffffff' $true
    Text $title 112 ($y+4) 27 '#142540' $true
    Text $subtitle 112 ($y+42) 18 '#52647e'
    for ($i=0; $i -lt 4; $i++) {
        $x = 64 + 380*$i
        Box $x ($y+80) $titles[$i] $lines[$i] $accent
        if ($i -lt 3 -and -not ($number -eq '4' -and $i -eq 2)) { Arrow ($x+338) ($y+136) ($x+370) ($y+136) }
    }
    Text $note 66 ($y+211) 18 '#52647e'
}
Text 'AUTHENTICATION / FLOW ARCHITECTURE' 48 28 36 '#142540' $true
Text 'Spring Boot  |  Spring Security  |  Spring Data JPA  |  Render PostgreSQL' 50 79 21 '#52647e'
Rect 40 124 1520 88 '#142540'
Text 'CLIENT' 65 140 20 '#ffffff' $true
Text 'Browser / API client' 65 172 18 '#c9d9f1'
Text 'APPLICATION' 470 140 20 '#ffffff' $true
Text 'Security filters > Controller > Service / AuthenticationManager' 470 172 18 '#c9d9f1'
Text 'STORAGE' 1210 140 20 '#ffffff' $true
Text 'PostgreSQL + HTTP session' 1210 172 18 '#c9d9f1'
Row 234 '1' 'Get a CSRF token' 'Before registration or login: start a session and keep its cookie.' '#3877d6' @('Client','Spring Security','HTTP session','Client receives') @("GET /auth/csrf`nNo login required", "Create / load CSRF token`nController returns token", "Store CSRF token`nApplication memory", "Token + headerName`nJSESSIONID cookie") 'POST requests must send the session cookie and the token in the X-CSRF-TOKEN header.'
Row 504 '2' 'Register an account' 'POST /auth/register with email, password, cookie and CSRF token.' '#198573' @('Security + controller','AuthService','UserRepository / JPA','Render PostgreSQL') @("Check CSRF token`nValidate RegisterRequest", "Normalize email; check user`nBCrypt hashes password", "saveAndFlush(User)`nPersist through JDBC", "app_users: email + hash`nUnique email constraint") 'Success: 201 Created (login is still required). Invalid DTO: 400. Duplicate email: 409. Invalid CSRF: 403.'
Row 774 '3' 'Log in and save authentication' 'POST /auth/login with email, password, cookie and CSRF token.' '#7956b8' @('Security + controller','AuthenticationManager','Provider + JPA','HTTP session') @("Check CSRF token`nValidate LoginRequest", "DaoAuthenticationProvider`nDelegates credential checks", "Load user from PostgreSQL`nBCrypt verifies password", "Rotate session ID`nSave SecurityContext") 'Success: 200 + email and updated cookie. Invalid credentials: 401. Old CSRF token is cleared; fetch /auth/csrf again.'
Row 1044 '4' 'Use the session / log out' 'Later requests authenticate using the JSESSIONID cookie.' '#b17624' @('Client','Spring Security','Protected controller','Logout filter') @("GET /auth/me`nSend session cookie", "Restore SecurityContext`nCheck authentication", "200 + authenticated email`nNo valid session: 401", "POST /auth/logout + CSRF`nInvalidate session; 200") 'Logout is a separate request handled by Spring Security. The controller does not process the logout request.'
Text 'DATABASE CONFIG' 50 1320 18 '#142540' $true
Text 'DB_URL (JDBC + TLS), DB_USERNAME, DB_PASSWORD via environment variables. Credentials stay out of source code.' 245 1320 18 '#52647e'
Text 'SESSION LIFETIME' 50 1360 18 '#142540' $true
Text '30 minutes idle timeout. Sessions live in application memory and disappear on restart or redeployment.' 245 1360 18 '#52647e'
$output = Join-Path $PSScriptRoot 'images/authentication-flow.png'
$bitmap.Save($output, [System.Drawing.Imaging.ImageFormat]::Png)
$g.Dispose(); $bitmap.Dispose()


