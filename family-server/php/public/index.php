<?php
declare(strict_types=1);
ini_set('display_errors','0');
require_once dirname(__DIR__).'/private/bootstrap.php';
header('Cache-Control: no-store');header('X-Content-Type-Options: nosniff');header('Referrer-Policy: no-referrer');
header("Content-Security-Policy: default-src 'self'; script-src 'self'; style-src 'self' 'unsafe-inline'; img-src 'self' data:; connect-src 'self'; frame-ancestors 'none'; base-uri 'none'; form-action 'self'");
header('Permissions-Policy: geolocation=(), camera=(), microphone=()');
function browserCookie(string $token,int $maxAge): void {
    header('Set-Cookie: __Host-osmon_parent='.$token.'; Path=/; HttpOnly; Secure; SameSite=Strict; Max-Age='.$maxAge,false);
}
try {
    $c=familyConfig();
    // Never trust arbitrary forwarded HTTPS / IP headers from clients.
    if (!in_array(strtolower((string)($_SERVER['HTTPS']??'')),['on','1'],true)) fail(400,'HTTPS erforderlich.');
    $expected=parse_url($c['origin'],PHP_URL_HOST).(parse_url($c['origin'],PHP_URL_PORT)?':'.parse_url($c['origin'],PHP_URL_PORT):'');
    if (strtolower($_SERVER['HTTP_HOST']??'')!==strtolower($expected)) fail(421,'Ungültiger Servername.');
    header('Strict-Transport-Security: max-age=15552000');
    $method=$_SERVER['REQUEST_METHOD']??'GET';$path=$_SERVER['REQUEST_URI']??'/';
    if (strlen($path)>256 || str_contains($path,'?') || str_contains($path,'%')) fail(400,'Ungültiger Pfad.');
    $assets=['/'=>'index.html','/app.js'=>'app.js','/style.css'=>'style.css','/lumo.svg'=>'lumo.svg'];
    if ($method==='GET' && isset($assets[$path])) {
        $file=__DIR__.'/'.$assets[$path];
        if (!is_file($file)) $file=dirname(__DIR__,2).'/web/'.$assets[$path];
        if (!is_file($file)) fail(503,'Webdateien fehlen.');
        $types=['html'=>'text/html; charset=utf-8','js'=>'text/javascript; charset=utf-8','css'=>'text/css; charset=utf-8','svg'=>'image/svg+xml'];
        header('Content-Type: '.$types[pathinfo($file,PATHINFO_EXTENSION)]);readfile($file);exit;
    }
    if (!str_starts_with($path,'/v1/') && !str_starts_with($path,'/web-api/')) fail(404,'Nicht gefunden.');
    $db=connectFamily($c);$store=new FamilyStore($db);
    $store->rate($_SERVER['REMOTE_ADDR']??'unknown',$path,$c['rate_secret']);
    if (isset($_SERVER['HTTP_TRANSFER_ENCODING'])) fail(400,'Nicht unterstützte Übertragung.');
    $length=$_SERVER['CONTENT_LENGTH']??'0';
    if (!ctype_digit((string)$length) || (int)$length>16384) fail(413,'Anfrage zu groß.');
    $raw=file_get_contents('php://input',false,null,0,16385);
    if ($raw===false || strlen($raw)>16384) fail(413,'Anfrage zu groß.');
    $body=[];
    if ($raw!=='') {
        $obj=json_decode($raw,false,32,JSON_THROW_ON_ERROR);
        if (!$obj instanceof stdClass) fail(400,'JSON-Objekt erforderlich.');
        $body=json_decode($raw,true,32,JSON_THROW_ON_ERROR);
    }
    $auth=$_SERVER['HTTP_AUTHORIZATION']??$_SERVER['REDIRECT_HTTP_AUTHORIZATION']??'';
    $token=str_starts_with($auth,'Bearer ')?substr($auth,7):'';
    if (str_starts_with($path,'/web-api/')) {
        if ($method!=='GET' && (($_SERVER['HTTP_ORIGIN']??'')!==$c['origin'] || ($_SERVER['HTTP_X_OSMON_WEB']??'')!=='1')) fail(403,'Anfrage von dieser Webseite nicht erlaubt.');
        $token=$_COOKIE['__Host-osmon_parent']??'';
        if (!is_string($token) || strlen($token)>100) fail(401,'Ungültige Sitzung.');
        $sub=substr($path,9);
        if ($sub==='status' && $method==='GET') {
            $store->cleanup();$authenticated=false;
            try { $store->parent($token);$authenticated=true; } catch (FamilyAPIError $e) { if ($e->status!==401) throw $e; }
            $result=['configured'=>true,'authenticated'=>$authenticated];
        } elseif (in_array($sub,['access','login'],true) && $method==='POST') {
            $result=$store->dispatch('POST',$sub==='access'?'/v1/browser-access':'/v1/web-codes/redeem','',$body);
            // Rotate current browser session after successful sign-in.
            if ($token!=='') $store->dispatch('DELETE','/v1/browser-session',$token,[]);
            browserCookie($result['token'],604800);$result=['ok'=>true];
        } elseif ($sub==='logout' && $method==='DELETE') {
            $result=$store->dispatch('DELETE','/v1/browser-session',$token,[]);browserCookie('',0);
        } elseif (in_array($sub,['children','claims','parents'],true) || str_starts_with($sub,'children/')) {
            $result=$store->dispatch($method,'/v1/'.$sub,$token,$body);
            if ($sub==='parents' && $method==='DELETE') browserCookie('',0);
        } else fail(404,'Nicht gefunden.');
    } else {
        if (strlen($token)>100) fail(401,'Ungültiger Zugang.');
        $result=$store->dispatch($method,$path,$token,$body);
    }
    $status=200;
} catch (FamilyAPIError $e) { $status=$e->status;$result=['error'=>$e->getMessage()]; }
catch (JsonException|ValueError $e) { $status=400;$result=['error'=>'Ungültiges JSON.']; }
catch (Throwable $e) { $status=503;$result=['error'=>'Familienserver derzeit nicht verfügbar.']; /* Never log tokens, passwords, locations or request payloads. */ }
http_response_code($status);header('Content-Type: application/json; charset=utf-8');
if ($status===429) header('Retry-After: 60');
echo encodeJSON($result);
