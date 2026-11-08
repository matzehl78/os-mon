<?php
declare(strict_types=1);
require_once __DIR__.'/SetupWizard.php';
function handleSetupHTTP():never{
    try{
        $directory=dirname(getenv('OSMON_CONFIG_FILE')?:__DIR__.'/config.php');
        $doc=realpath($_SERVER['DOCUMENT_ROOT']??'');$private=realpath($directory);
        if(!$doc||!$private||$doc===$private||str_starts_with($private,$doc.DIRECTORY_SEPARATOR))fail(503,'Privates Verzeichnis muss außerhalb des Webverzeichnisses liegen.');
        $wizard=new SetupWizard($directory);$origin=$wizard->origin();
        if(!in_array(strtolower((string)($_SERVER['HTTPS']??'')),['on','1'],true))fail(400,'HTTPS erforderlich.');
        $host=parse_url($origin,PHP_URL_HOST).(parse_url($origin,PHP_URL_PORT)?':'.parse_url($origin,PHP_URL_PORT):'');
        if(strtolower($_SERVER['HTTP_HOST']??'')!==strtolower($host))fail(421,'Falscher Servername für die Einrichtung.');
        header('Strict-Transport-Security: max-age=15552000');
        $path=$_SERVER['REQUEST_URI']??'';$m=$_SERVER['REQUEST_METHOD']??'GET';
        if(strlen($path)>128||str_contains($path,'?')||str_contains($path,'%'))fail(400,'Keine URL-Parameter für die Einrichtung erlaubt.');
        $assets=['/setup'=>'setup.html','/setup/'=>'setup.html','/setup.js'=>'setup.js','/setup.css'=>'setup.css'];
        if($m==='GET'&&isset($assets[$path])){
            $file=dirname(__DIR__).'/public/'.$assets[$path];$types=['html'=>'text/html; charset=utf-8','js'=>'text/javascript; charset=utf-8','css'=>'text/css; charset=utf-8'];
            header('Content-Type: '.$types[pathinfo($file,PATHINFO_EXTENSION)]);readfile($file);exit;
        }
        if(!str_starts_with($path,'/setup-api/'))fail(404,'Nicht gefunden.');
        if($m!=='GET'&& (($_SERVER['HTTP_ORIGIN']??'')!==$origin||($_SERVER['HTTP_X_OSMON_SETUP']??'')!=='1'))fail(403,'Einrichtungsanfrage von dieser Webseite nicht erlaubt.');
        $length=$_SERVER['CONTENT_LENGTH']??'0';if(!ctype_digit((string)$length)||(int)$length>16384||isset($_SERVER['HTTP_TRANSFER_ENCODING']))fail(413,'Anfrage zu groß oder nicht unterstützt.');
        $raw=file_get_contents('php://input',false,null,0,16385);if($raw===false||strlen($raw)>16384)fail(413,'Anfrage zu groß.');
        $b=[];if($raw!==''){$decoded=json_decode($raw,false,32,JSON_THROW_ON_ERROR);if(!($decoded instanceof stdClass))fail(400,'JSON-Objekt erforderlich.');$b=json_decode($raw,true,32,JSON_THROW_ON_ERROR);}
        $session=$_COOKIE['__Host-osmon_setup']??'';if(!is_string($session)||strlen($session)>100)fail(401,'Ungültige Sitzung.');
        $result=$wizard->dispatch($m,substr($path,11),$session,$b,$_SERVER['HTTP_X_OSMON_CSRF']??'',$_SERVER['REMOTE_ADDR']??'unknown');
        if(isset($result['session'])){header('Set-Cookie: __Host-osmon_setup='.$result['session'].'; Path=/; HttpOnly; Secure; SameSite=Strict; Max-Age=1800',false);unset($result['session']);}
        if(!empty($result['complete']))header('Set-Cookie: __Host-osmon_setup=; Path=/; HttpOnly; Secure; SameSite=Strict; Max-Age=0',false);
        $status=200;
    }catch(FamilyAPIError$e){$status=$e->status;$result=['error'=>$e->getMessage()];}
    catch(JsonException|ValueError$e){$status=400;$result=['error'=>'Ungültige Anfrage.'];}
    catch(Throwable$e){$status=503;$result=['error'=>'Einrichtung fehlgeschlagen. Verzeichnisrechte und Servervoraussetzungen lokal prüfen.'];}
    http_response_code($status);header('Content-Type: application/json; charset=utf-8');if($status===429)header('Retry-After: 60');echo encodeJSON($result);exit;
}
