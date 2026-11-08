<?php
declare(strict_types=1);
require_once __DIR__.'/AdminStore.php';

final class SetupWizard {
    private Closure $clock;
    public function __construct(private string $directory,?Closure $clock=null) { $this->clock=$clock??static fn()=>microtime(true); }
    private function now():float{return (float)($this->clock)();}
    private function path(string $name):string{return $this->directory.'/'.$name;}
    public function complete():bool{return is_file($this->path('config.php'))||is_file($this->path('.setup-complete'));}
    public function origin():string{
        if(is_file($this->path('.setup-state.json'))){$s=json_decode((string)file_get_contents($this->path('.setup-state.json')),true);if(is_array($s)&&isset($s['origin']))return $s['origin'];}
        if(is_file($this->path('config.php'))){$c=require $this->path('config.php');if(is_array($c)&&isset($c['origin']))return $c['origin'];}
        return getenv('OSMON_SETUP_ORIGIN') ?: 'https://parents.ulmatti-studios.com';
    }
    private function save(array $state):void {
        $p=$this->path('.setup-state.json');$h=fopen($p,'c+b');if(!$h)fail(503,'Einrichtungsdatei nicht beschreibbar.');
        try{chmod($p,0600);ftruncate($h,0);rewind($h);$data=encodeJSON($state);if(fwrite($h,$data)!==strlen($data)||!fflush($h))fail(503,'Einrichtungsdatei konnte nicht gespeichert werden.');}finally{fclose($h);}
    }
    private function locked(Closure $fn):mixed{
        if(!is_dir($this->directory))fail(503,'Privates Verzeichnis fehlt.');
        $path=$this->path('.setup.lock');$h=fopen($path,'c+b');if(!$h)fail(503,'Privates Verzeichnis nicht beschreibbar.');chmod($path,0600);
        if(!flock($h,LOCK_EX)){fclose($h);fail(503,'Einrichtung gesperrt.');}
        try{return $fn();}finally{flock($h,LOCK_UN);fclose($h);}
    }
    public function start(string $origin):string{
        $u=parse_url($origin);
        if(!$u||($u['scheme']??'')!=='https'||empty($u['host'])||!preg_match('/^[a-zA-Z0-9.-]+$/D',$u['host'])||isset($u['user'])||isset($u['pass'])||isset($u['query'])||isset($u['fragment'])||!empty($u['path']))fail(400,'HTTPS-Adresse ohne Pfad erforderlich.');
        return $this->locked(function()use($origin){
            if($this->complete())fail(409,'Bereits eingerichtet. Der Assistent überschreibt keine bestehende Konfiguration.');
            $code=newToken();$state=['origin'=>$origin,'expires'=>$this->now()+1800,'codeHash'=>tokenHash($code),'sessionHash'=>null,'csrf'=>newToken(),'stage'=>'unlock','attempts'=>[]];
            $this->save($state);return $code;
        });
    }
    private function activateFromFile():void{
        $file=$this->path('setup-enable.txt');
        if(is_file($this->path('.setup-state.json'))||!is_file($file))return;
        if(is_link($file)||filesize($file)>128)fail(400,'Freischaltdatei ungültig. Nur einen zufälligen Code mit 32–100 Zeichen eintragen.');
        $raw=file_get_contents($file,false,null,0,129);
        $code=trim(preg_replace('/^\xEF\xBB\xBF/','',(string)$raw));
        if(!preg_match('/^[A-Za-z0-9_-]{32,100}$/D',$code)||preg_match('/^(HIER|REPLACE|CHANGE|DEIN_)/i',$code))fail(400,'In setup-enable.txt einen eigenen zufälligen Code mit 32–100 Zeichen (Buchstaben, Ziffern, _ oder -) eintragen.');
        @chmod($file,0600);
        if(!unlink($file))fail(503,'Freischaltdatei konnte nicht entfernt werden. Private Verzeichnisrechte prüfen.');
        $this->save(['origin'=>$this->origin(),'expires'=>$this->now()+1800,'codeHash'=>tokenHash($code),'sessionHash'=>null,'csrf'=>newToken(),'stage'=>'unlock','attempts'=>[]]);
    }
    private function checklist():array{
        return ['php'=>version_compare(PHP_VERSION,'8.3','>='),'pdo_mysql'=>extension_loaded('pdo_mysql'),'mbstring'=>extension_loaded('mbstring'),'openssl'=>extension_loaded('openssl'),'privateWritable'=>is_writable($this->directory),'phpVersion'=>PHP_VERSION];
    }
    private function view(array $s):array{
        $out=['complete'=>false,'authenticated'=>true,'stage'=>$s['stage'],'csrf'=>$s['csrf'],'expires'=>$s['expires'],'checks'=>$this->checklist(),'origin'=>$s['origin']];
        if(isset($s['dbVersion']))$out['databaseVersion']=$s['dbVersion'];
        if($s['stage']==='authenticator'){$out['secret']=base32Encode(base64_decode($s['totp'],true));$out['username']=$s['adminName'];}
        return $out;
    }
    public function dispatch(string $method,string $route,string $session,array $body,string $csrf,string $ip):array{
        return $this->locked(function()use($method,$route,$session,$body,$csrf,$ip){
            if($this->complete()){
                if($method==='GET'&&$route==='status')return ['complete'=>true,'authenticated'=>false];
                fail(410,'Einrichtung bereits abgeschlossen.');
            }
            $this->activateFromFile();
            $path=$this->path('.setup-state.json');
            if(!is_file($path))fail(503,'Zuerst die private Datei setup-enable.txt mit deinem zufälligen Einrichtungscode anlegen.');
            $s=json_decode((string)file_get_contents($path),true,32,JSON_THROW_ON_ERROR);
            if(!is_array($s)||($s['expires']??0)<=$this->now()) { @unlink($path);fail(410,'Einrichtungscode abgelaufen. Neue private Freischaltdatei anlegen und Seite neu laden.'); }
            try{
                if($method==='POST'&&$route==='unlock'){
                    $minute=(int)floor($this->now()/60);$key=hash_hmac('sha256',$ip,$s['csrf']);
                    $s['attempts']=array_filter($s['attempts'],fn($v)=>$v['minute']>=$minute-1);
                    $rate=$s['attempts'][$key]??['minute'=>$minute,'count'=>0];if($rate['minute']!==$minute)$rate=['minute'=>$minute,'count'=>0];
                    $rate['count']++;$s['attempts'][$key]=$rate;
                    if($rate['count']>6)fail(429,'Zu viele Versuche. Eine Minute warten.');
                    $code=$body['code']??'';
                    if(!is_string($code)||strlen($code)>100||!$s['codeHash']||!hash_equals($s['codeHash'],tokenHash($code)))fail(401,'Einrichtungscode ungültig oder bereits verwendet.');
                    $raw=newToken();$s['sessionHash']=tokenHash($raw);$s['codeHash']=null;$s['stage']='database';
                    return array_merge($this->view($s),['session'=>$raw]);
                }
                $valid=$session!==''&&is_string($s['sessionHash'])&&hash_equals($s['sessionHash'],tokenHash($session));
                if($method==='GET'&&$route==='status')return $valid?$this->view($s):['complete'=>false,'authenticated'=>false,'stage'=>'unlock'];
                if(!$valid)fail(401,'Einrichtungssitzung ungültig. Bei Browserverlust die private .setup-state.json entfernen und neu freischalten.');
                if(!hash_equals($s['csrf'],$csrf))fail(403,'Ungültige Einrichtungsanfrage. Seite neu laden.');
                if($method!=='POST')fail(405,'Nicht unterstützt.');
                if($route==='database'){
                    if(!in_array($s['stage'],['database','admin'],true))fail(409,'Datenbank kann in diesem Schritt nicht geändert werden.');
                    foreach($this->checklist()as$k=>$v)if($k!=='phpVersion'&&!$v)fail(503,'Servervoraussetzungen fehlen. Prüfliste beachten.');
                    $host=$body['host']??'';$port=$body['port']??null;$name=$body['database']??'';$user=$body['username']??'';$pass=$body['password']??'';
                    if(!is_string($host)||!preg_match('/^[a-zA-Z0-9.-]{1,253}$/D',$host)||!is_int($port)||$port<1||$port>65535||!is_string($name)||!preg_match('/^[a-zA-Z0-9_]{1,64}$/D',$name)||!is_string($user)||!preg_match('/^[a-zA-Z0-9_.-]{1,80}$/D',$user)||!is_string($pass)||strlen($pass)<1||strlen($pass)>1024)fail(400,'Datenbankangaben ungültig.');
                    $c=['origin'=>$s['origin'],'host'=>$host,'port'=>$port,'database'=>$name,'username'=>$user,'password'=>$pass,'rate_secret'=>bin2hex(random_bytes(32)),'admin_key'=>bin2hex(random_bytes(32))];
                    $fingerprint=hash_hmac('sha256',encodeJSON([$host,$port,$name,$user]),$s['csrf']);
                    try{$db=connectFamily($c);$version=(string)$db->query('SELECT VERSION()')->fetchColumn();$tables=$db->query('SHOW TABLES')->fetchAll(PDO::FETCH_COLUMN);}catch(PDOException$e){fail(400,'Datenbankverbindung fehlgeschlagen. Host, Datenbankname, Benutzer und Passwort prüfen.');}
                    if(version_compare($version,'5.7','<'))fail(400,'Mindestens MySQL 5.7 erforderlich.');
                    if($tables&&($s['databaseIntent']??null)!==$fingerprint)fail(409,'Bitte eine leere, separate Datenbank verwenden. Bestehende Tabellen werden nicht übernommen.');
                    if($tables){foreach($tables as$table)if(!str_starts_with($table,'osmon_'))fail(409,'Fremde Tabellen vorhanden. Einrichtung abgebrochen.');}
                    // Persist intent before nontransactional DDL so retries cannot target an unrelated database.
                    $s['databaseIntent']=$fingerprint;$s['config']=$c;$this->save($s);
                    try{foreach(explode(';',(string)file_get_contents(__DIR__.'/schema.sql'))as$sql)if(trim($sql)!=='')$db->exec($sql);}catch(PDOException$e){fail(400,'Tabellen konnten nicht eingerichtet werden. Datenbankrechte prüfen und erneut versuchen.');}
                    if((int)$db->query('SELECT count(*) FROM osmon_admins')->fetchColumn()>0)fail(409,'Datenbank enthält bereits Adminzugänge. Einrichtung abgebrochen.');
                    $s['dbVersion']=$version;$s['stage']='admin';return $this->view($s);
                }
                if($route==='admin'){
                    if(!in_array($s['stage'],['admin','authenticator'],true))fail(409,'Zuerst Datenbank einrichten.');
                    $name=$body['username']??'';$pass=$body['password']??'';$repeat=$body['repeat']??'';
                    if(!is_string($name)||!preg_match('/^[a-z0-9._-]{3,40}$/D',$name)||!is_string($pass)||strlen($pass)<12||strlen($pass)>72||!is_string($repeat)||!hash_equals($pass,$repeat))fail(400,'Adminname: 3–40 Zeichen. Passwort: 12–72 Bytes; beide Eingaben müssen übereinstimmen.');
                    $s['adminName']=$name;$s['passwordHash']=password_hash($pass,defined('PASSWORD_ARGON2ID')?PASSWORD_ARGON2ID:PASSWORD_DEFAULT);$s['totp']=base64_encode(random_bytes(20));$s['stage']='authenticator';return $this->view($s);
                }
                if($route==='finish'){
                    if($s['stage']!=='authenticator')fail(409,'Zuerst Adminzugang einrichten.');
                    $code=$body['code']??'';if(!is_string($code))fail(400,'Sechsstelligen Authenticator-Code eingeben.');
                    // Bound OTP guessing within the same setup session.
                    $s['otpFailures']=(int)($s['otpFailures']??0)+1;if($s['otpFailures']>10)fail(429,'Zu viele Codeversuche. Private .setup-state.json entfernen und mit neuer Freischaltdatei starten.');
                    $secret=base64_decode($s['totp'],true);$step=matchTOTP($secret,$code,$this->now(),-1);if($step===null)fail(400,'Authenticator-Code ungültig. Uhrzeit prüfen und neuen Code eingeben.');
                    $db=connectFamily($s['config']);$db->beginTransaction();$wrote=false;$marked=false;
                    try{
                        $db->query('SELECT id FROM osmon_lock WHERE id=1 FOR UPDATE')->fetch();
                        if((int)$db->query('SELECT count(*) FROM osmon_admins')->fetchColumn()>0)fail(409,'Adminzugang bereits vorhanden.');
                        $id=newUUID();$sealed=encryptAdminSecret($secret,hex2bin($s['config']['admin_key']),$id);
                        $q=$db->prepare('INSERT INTO osmon_admins(id,username,password_hash,totp_secret,last_step,created) VALUES (?,?,?,?,?,?)');$q->execute([$id,$s['adminName'],$s['passwordHash'],$sealed,$step,$this->now()]);
                        $q=$db->prepare('INSERT INTO osmon_admin_audit(happened,actor,action,target) VALUES (?,?,?,?)');$q->execute([$this->now(),$s['adminName'],'admin_created',$id]);
                        $h=fopen($this->path('config.php'),'x');if(!$h)fail(409,'Konfiguration bereits vorhanden oder nicht beschreibbar.');$wrote=true;
                        try{chmod($this->path('config.php'),0600);$data="<?php\nreturn ".var_export($s['config'],true).";\n";if(fwrite($h,$data)!==strlen($data)||!fflush($h))fail(503,'Konfiguration konnte nicht geschrieben werden.');}finally{fclose($h);}
                        $h=fopen($this->path('.setup-complete'),'x');if(!$h)fail(503,'Einrichtungssperre konnte nicht gesetzt werden.');$marked=true;chmod($this->path('.setup-complete'),0600);fclose($h);
                        $db->commit();$s['finished']=true;return ['complete'=>true,'authenticated'=>false,'checks'=>['database'=>true,'admin'=>true,'locked'=>true],'adminPath'=>'/admin'];
                    }catch(Throwable$e){if($db->inTransaction())$db->rollBack();if($wrote)@unlink($this->path('config.php'));if($marked)@unlink($this->path('.setup-complete'));throw $e;}
                }
                fail(404,'Nicht gefunden.');
            }finally{if(!empty($s['finished']))@unlink($path);else $this->save($s);}
        });
    }
}
