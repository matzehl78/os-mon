<?php
declare(strict_types=1);
require_once __DIR__.'/FamilyStore.php';
function adminKey(array $config): string {
    $hex=$config['admin_key']??'';
    if (!is_string($hex) || !preg_match('/^[a-f0-9]{64}$/D',$hex)) fail(503,'Adminbereich noch nicht eingerichtet.');
    return hex2bin($hex);
}
function base32Encode(string $raw): string {
    $alphabet='ABCDEFGHIJKLMNOPQRSTUVWXYZ234567';$out='';$bits=0;$value=0;
    foreach (unpack('C*',$raw) as $byte) {
        $value=($value<<8)|$byte;$bits+=8;
        while ($bits>=5) { $bits-=5;$out.=$alphabet[($value>>$bits)&31]; }
        $value&=(1<<$bits)-1;
    }
    if ($bits) $out.=$alphabet[($value<<(5-$bits))&31];return $out;
}
function base32Decode(string $encoded): string {
    if (!preg_match('/^[A-Z2-7]+$/D',$encoded)) throw new RuntimeException('Invalid authenticator secret');
    $alphabet='ABCDEFGHIJKLMNOPQRSTUVWXYZ234567';$out='';$bits=0;$value=0;
    foreach (str_split($encoded) as $char) {
        $value=($value<<5)|strpos($alphabet,$char);$bits+=5;
        if ($bits>=8) { $bits-=8;$out.=chr(($value>>$bits)&255); }
        $value&=(1<<$bits)-1;
    }return $out;
}
function totpCode(string $rawSecret,int $step,int $digits=6): string {
    $counter=pack('N2',intdiv($step,4294967296),$step%4294967296);
    $mac=hash_hmac('sha1',$counter,$rawSecret,true);$offset=ord($mac[19])&15;
    $number=unpack('N',substr($mac,$offset,4))[1]&0x7fffffff;
    return str_pad((string)($number%(10**$digits)),$digits,'0',STR_PAD_LEFT);
}
function matchTOTP(string $rawSecret,string $code,float $now,int $lastStep): ?int {
    if (!preg_match('/^[0-9]{6}$/D',$code)) return null;
    $step=(int)floor($now/30);
    for ($i=1;$i>=-1;$i--) if ($step+$i>$lastStep && hash_equals(totpCode($rawSecret,$step+$i),$code)) return $step+$i;
    return null;
}
function encryptAdminSecret(string $secret,string $key,string $adminID): string {
    $nonce=random_bytes(12);$tag='';
    $cipher=openssl_encrypt($secret,'aes-256-gcm',$key,OPENSSL_RAW_DATA,$nonce,$tag,'osmon-admin:'.$adminID,16);
    if ($cipher===false) throw new RuntimeException('Encryption failed');return base64_encode($nonce.$tag.$cipher);
}
function decryptAdminSecret(string $sealed,string $key,string $adminID): string {
    $raw=base64_decode($sealed,true);if ($raw===false || strlen($raw)<29) throw new RuntimeException('Invalid encrypted secret');
    $plain=openssl_decrypt(substr($raw,28),'aes-256-gcm',$key,OPENSSL_RAW_DATA,substr($raw,0,12),substr($raw,12,16),'osmon-admin:'.$adminID);
    if ($plain===false) throw new RuntimeException('Decryption failed');return $plain;
}
final class AdminStore {
    private Closure $clock;
    public function __construct(private PDO $db,private string $key,?Closure $clock=null) { $this->clock=$clock??static fn()=>microtime(true); }
    private function now(): float { return (float)($this->clock)(); }
    private function q(string $sql,array $params=[]): PDOStatement { $s=$this->db->prepare($sql);$s->execute($params);return $s; }
    private function one(string $sql,array $params=[]): array|false { return $this->q($sql,$params)->fetch(); }
    private function locked(Closure $action): mixed {
        $this->db->beginTransaction();
        try { $this->q('SELECT id FROM osmon_lock WHERE id=1 FOR UPDATE')->fetch();$v=$action();$this->db->commit();return $v; }
        catch(Throwable $e){if($this->db->inTransaction())$this->db->rollBack();throw $e;}
    }
    private function audit(string $actor,string $action,string $target): void {
        $this->q('INSERT INTO osmon_admin_audit(happened,actor,action,target) VALUES (?,?,?,?)',[$this->now(),$actor,$action,$target]);
    }
    public function provision(string $username,string $password,string $secret,string $code): void {
        if (!preg_match('/^[a-z0-9._-]{3,40}$/D',$username) || strlen($password)<12 || strlen($password)>72 || strlen($secret)<20) fail(400,'Adminname oder Passwort ungültig.');
        $step=matchTOTP($secret,$code,$this->now(),-1);if($step===null)fail(400,'Authenticator-Code ungültig.');
        $hash=password_hash($password,defined('PASSWORD_ARGON2ID')?PASSWORD_ARGON2ID:PASSWORD_DEFAULT);
        $this->locked(function()use($username,$hash,$secret,$step){
            $old=$this->one('SELECT id FROM osmon_admins WHERE username=?',[$username]);$id=$old?$old['id']:newUUID();
            $sealed=encryptAdminSecret($secret,$this->key,$id);
            if($old){
                $this->q('UPDATE osmon_admins SET password_hash=?,totp_secret=?,last_step=?,failures=0,locked_until=0 WHERE id=?',[$hash,$sealed,$step,$id]);
                $this->q('DELETE FROM osmon_admin_sessions WHERE admin_id=?',[$id]);
            }else $this->q('INSERT INTO osmon_admins(id,username,password_hash,totp_secret,last_step,created) VALUES (?,?,?,?,?,?)',[$id,$username,$hash,$sealed,$step,$this->now()]);
            $this->audit($username,$old?'admin_reset':'admin_created',$id);
        });
    }
    public function login(array $body): array {
        $name=$body['username']??'';$pass=$body['password']??'';$code=$body['code']??'';
        if(!is_string($name)||!is_string($pass)||!is_string($code)||strlen($pass)>72||!preg_match('/^[a-z0-9._-]{3,40}$/D',$name))fail(401,'Anmeldung fehlgeschlagen.');
        // Failed-attempt counters must commit even when login fails.
        $result=$this->locked(function()use($name,$pass,$code){
            $a=$this->one('SELECT * FROM osmon_admins WHERE username=?',[$name]);$now=$this->now();
            $dummy='$2y$10$92IXUNpkjO0rOQ5byMi.Ye4oKoEa3Ro9llC/.og/at2uheWG/igi.';
            $passwordOK=password_verify($pass,$a?$a['password_hash']:$dummy);
            $step=null;
            if($a&&$passwordOK&&(float)$a['locked_until']<=$now) $step=matchTOTP(decryptAdminSecret($a['totp_secret'],$this->key,$a['id']),$code,$now,(int)$a['last_step']);
            if(!$a||!$passwordOK||$step===null||(float)$a['locked_until']>$now){
                if($a&&(float)$a['locked_until']<=$now){$fails=(int)$a['failures']+1;$this->q('UPDATE osmon_admins SET failures=?,locked_until=? WHERE id=?',[$fails>=5?0:$fails,$fails>=5?$now+900:0,$a['id']]);}
                return null;
            }
            $this->q('UPDATE osmon_admins SET last_step=?,failures=0,locked_until=0 WHERE id=?',[$step,$a['id']]);
            $this->q('DELETE FROM osmon_admin_sessions WHERE admin_id=?',[$a['id']]);
            $raw=newToken();$this->q('INSERT INTO osmon_admin_sessions(token,admin_id,created,touched,expires) VALUES (?,?,?,?,?)',[tokenHash($raw),$a['id'],$now,$now,$now+7200]);
            $this->audit($a['username'],'login',$a['id']);
            return ['token'=>$raw,'username'=>$a['username'],'expires'=>$now+7200,'csrf'=>$this->csrf($raw)];
        });
        if(!$result)fail(401,'Anmeldung fehlgeschlagen.');return $result;
    }
    private function csrf(string $raw): string { return hash_hmac('sha256','osmon-admin-csrf:'.$raw,$this->key); }
    private function session(string $raw): array {
        $row=$this->one('SELECT s.*,a.username FROM osmon_admin_sessions s JOIN osmon_admins a ON a.id=s.admin_id WHERE s.token=? AND s.expires>? AND s.touched>?',[tokenHash($raw),$this->now(),$this->now()-1800]);
        if(!$row)fail(401,'Admin-Sitzung abgelaufen.');
        $this->q('UPDATE osmon_admin_sessions SET touched=? WHERE token=?',[$this->now(),tokenHash($raw)]);return $row;
    }
    public function dispatch(string $m,string $path,string $raw,array $body,string $csrf=''): array {
        if($m==='POST'&&$path==='/admin-api/login')return $this->login($body);
        return $this->locked(function()use($m,$path,$raw,$body,$csrf){
            $a=$this->session($raw);
            if($m!=='GET'&&!hash_equals($this->csrf($raw),$csrf))fail(403,'Admin-Anfrage ungültig. Bitte neu laden.');
            if(($m==='GET'&&$path==='/admin-api/mail')||($m==='PUT'&&$path==='/admin-api/mail')||($m==='POST'&&$path==='/admin-api/mail/test'))return ['mailAuthorized'=>true];
            if($m==='GET'&&$path==='/admin-api/status')return ['authenticated'=>true,'username'=>$a['username'],'csrf'=>$this->csrf($raw),'expires'=>(float)$a['expires']];
            if($m==='DELETE'&&$path==='/admin-api/logout'){$this->q('DELETE FROM osmon_admin_sessions WHERE token=?',[tokenHash($raw)]);$this->audit($a['username'],'logout',$a['admin_id']);return ['ok'=>true];}
            if($m==='GET'&&$path==='/admin-api/overview'){
                $parents=$this->one('SELECT count(*) AS total,coalesce(sum(suspended),0) AS blocked FROM osmon_parents');
                $devices=$this->one('SELECT count(*) AS total,coalesce(sum(owner IS NOT NULL),0) AS paired,coalesce(sum(seen>?),0) AS recent FROM osmon_devices',[$this->now()-86400]);
                return ['families'=>(int)$parents['total'],'suspended'=>(int)$parents['blocked'],'devices'=>(int)$devices['total'],'paired'=>(int)$devices['paired'],'recent'=>(int)$devices['recent'],'serverTime'=>$this->now(),'php'=>PHP_VERSION,'database'=>'erreichbar'];
            }
            if($m==='POST'&&$path==='/admin-api/families'){
                $page=$body['page']??0;if(!is_int($page)||$page<0||$page>100000)fail(400,'Ungültige Seite.');
                $rows=$this->q('SELECT token,public_id,expires,suspended FROM osmon_parents ORDER BY expires,token LIMIT 101 OFFSET '.($page*100))->fetchAll();$more=count($rows)>100;$rows=array_slice($rows,0,100);$list=[];
                foreach($rows as $p){
                    if(!$p['public_id']){$p['public_id']=newUUID();$this->q('UPDATE osmon_parents SET public_id=? WHERE token=?',[$p['public_id'],$p['token']]);}
                    $stats=$this->one('SELECT count(*) AS devices,max(seen) AS seen FROM osmon_devices WHERE owner=?',[$p['token']]);
                    $list[]=['id'=>$p['public_id'],'expires'=>(float)$p['expires'],'suspended'=>(bool)$p['suspended'],'devices'=>(int)$stats['devices'],'seen'=>$stats['seen']===null?null:(float)$stats['seen']];
                }return ['families'=>$list,'page'=>$page,'more'=>$more];
            }
            if($m==='GET'&&$path==='/admin-api/audit'){
                $rows=$this->q('SELECT happened,actor,action,target FROM osmon_admin_audit ORDER BY id DESC LIMIT 100')->fetchAll();foreach($rows as &$r)$r['happened']=(float)$r['happened'];return ['events'=>$rows];
            }
            if(preg_match('~^/admin-api/families/([a-f0-9-]{36})(?:/(devices|suspend|resume))?$~D',$path,$parts)){
                $ref=$parts[1];$sub=$parts[2]??'';$p=$this->one('SELECT token FROM osmon_parents WHERE public_id=?',[$ref]);if(!$p)fail(404,'Familienzugang nicht gefunden.');
                if($m==='GET'&&$sub==='devices'){
                    $rows=$this->q('SELECT id,version,applied,seen FROM osmon_devices WHERE owner=? ORDER BY id',[$p['token']])->fetchAll();
                    foreach($rows as &$d){$d['version']=(int)$d['version'];$d['applied']=(int)$d['applied'];$d['seen']=$d['seen']===null?null:(float)$d['seen'];}return ['devices'=>$rows];
                }
                if(($m==='POST'&&in_array($sub,['suspend','resume'],true))||($m==='DELETE'&&$sub==='')){
                    if(($body['confirm']??null)!==$ref)fail(400,'Bestätigung fehlt.');
                    if($m==='DELETE')$this->q('DELETE FROM osmon_parents WHERE token=?',[$p['token']]);
                    else {
                        $this->q('UPDATE osmon_parents SET suspended=? WHERE token=?',[$sub==='suspend'?1:0,$p['token']]);
                        if($sub==='suspend'){
                            $this->q('DELETE FROM osmon_web_sessions WHERE owner=?',[$p['token']]);$this->q('DELETE FROM osmon_web_codes WHERE owner=?',[$p['token']]);
                            $this->q('UPDATE osmon_devices SET location=NULL,location_at=NULL,location_request=NULL,location_sharing=0 WHERE owner=?',[$p['token']]);
                        }
                    }
                    $this->audit($a['username'],$m==='DELETE'?'family_deleted':($sub==='suspend'?'family_suspended':'family_resumed'),$ref);return ['ok'=>true];
                }
            }
            if($m==='DELETE'&&preg_match('~^/admin-api/devices/([a-f0-9-]{36})$~D',$path,$parts)){
                $id=$parts[1];if(($body['confirm']??null)!==$id)fail(400,'Bestätigung fehlt.');
                if(!$this->one('SELECT id FROM osmon_devices WHERE id=?',[$id]))fail(404,'Gerät nicht gefunden.');
                $this->q('DELETE FROM osmon_devices WHERE id=?',[$id]);$this->audit($a['username'],'device_unlinked',$id);return ['ok'=>true];
            }
            fail(404,'Nicht gefunden.');
        });
    }
}
