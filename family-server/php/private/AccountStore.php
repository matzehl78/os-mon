<?php
declare(strict_types=1);
require_once __DIR__.'/FamilyStore.php';

/** A verified account owns a family; expiring sessions remain compatible with existing apps. */
final class AccountStore {
    private Closure $clock;
    public function __construct(private PDO $db,private string $origin,private Closure $deliver,?Closure $clock=null){$this->clock=$clock??static fn()=>microtime(true);}
    private function now():float{return (float)($this->clock)();}
    private function q(string $sql,array $p=[]):PDOStatement{$s=$this->db->prepare($sql);$s->execute($p);return $s;}
    private function one(string $sql,array $p=[]):array|false{return $this->q($sql,$p)->fetch();}
    public function ready():bool{return (bool)$this->db->query("SHOW TABLES LIKE 'osmon_accounts'")->fetchColumn()&&(bool)$this->db->query("SHOW TABLES LIKE 'osmon_account_tokens'")->fetchColumn();}
    public function install():void{foreach(explode(';',(string)file_get_contents(__DIR__.'/account-schema.sql'))as$sql)if(trim($sql)!=='')$this->db->exec($sql);}
    public function cleanup():void{$this->q('DELETE FROM osmon_account_tokens WHERE expires<=?',[$this->now()]);}
    public static function email(mixed $v):string{
        if(!is_string($v))fail(400,'Gültige E-Mail-Adresse eingeben.');$v=strtolower(trim($v));
        if(strlen($v)>254||!preg_match('/^[\x21-\x7e]+$/D',$v)||!filter_var($v,FILTER_VALIDATE_EMAIL))fail(400,'Gültige E-Mail-Adresse eingeben.');return $v;
    }
    private function password(mixed $p):string{if(!is_string($p)||strlen($p)<12||strlen($p)>72||str_contains($p,"\0"))fail(400,'Passwort: 12 bis 72 UTF-8-Bytes, ohne Nullzeichen.');return password_hash($p,defined('PASSWORD_ARGON2ID')?PASSWORD_ARGON2ID:PASSWORD_DEFAULT);}
    private function session(string $owner):array{
        $t=newToken();$expires=$this->now()+604800;$this->q('INSERT INTO osmon_web_sessions(token,owner,expires) VALUES (?,?,?)',[tokenHash($t),$owner,$expires]);return ['token'=>$t,'expires'=>$expires];
    }
    /** Change the formerly permanent bearer identity, retaining devices, settings and support ID. */
    private function rekey(array $a):string{
        $p=$this->one('SELECT * FROM osmon_parents WHERE token=?',[$a['owner']]);if(!$p)fail(401,'Familie nicht verfügbar.');
        $owner=tokenHash(newToken());$this->q('INSERT INTO osmon_parents(token,expires,suspended) VALUES (?,?,?)',[$owner,4102444800,$p['suspended']]);
        $this->q('UPDATE osmon_devices SET owner=? WHERE owner=?',[$owner,$a['owner']]);
        $this->q('UPDATE osmon_accounts SET owner=? WHERE id=?',[$owner,$a['id']]);
        $this->q('DELETE FROM osmon_parents WHERE token=?',[$a['owner']]);
        if($p['public_id'])$this->q('UPDATE osmon_parents SET public_id=? WHERE token=?',[$p['public_id'],$owner]);return $owner;
    }
    private function mail(array $a,string $purpose):?array{
        $now=$this->now();if($now-(float)$a['mail_at']<60)return null;
        $count=$now-(float)$a['mail_window']>=3600?0:(int)$a['mail_count'];if($count>=5)return null;
        $this->q('UPDATE osmon_accounts SET mail_at=?,mail_window=?,mail_count=? WHERE id=?',[$now,$count===0?$now:$a['mail_window'],$count+1,$a['id']]);
        $this->q('DELETE FROM osmon_account_tokens WHERE account_id=? AND purpose=?',[$a['id'],$purpose]);
        $raw=newToken();$this->q('INSERT INTO osmon_account_tokens(token,account_id,purpose,expires) VALUES (?,?,?,?)',[tokenHash($raw),$a['id'],$purpose,$now+($purpose==='verify'?86400:1800)]);
        $url=$this->origin.'/#account-'.$purpose.'='.$raw;
        return [$a['email'],$purpose==='verify'?'OS-Mon: E-Mail bestaetigen':'OS-Mon: Passwort zuruecksetzen',
            ($purpose==='verify'?"Bestätige deine E-Mail-Adresse und lege dein Passwort fest (24 Stunden gültig):\n":"Lege ein neues Passwort fest (30 Minuten gültig):\n").$url."\n\nÖffne den Link und bestätige die Aktion selbst. Falls du das nicht angefordert hast, ignoriere diese Nachricht.\n"];
    }
    public function dispatch(string $method,string $action,string $token,array $b):array{
        if(!$this->ready())fail(503,'Elternkonten müssen zuerst in der Verwaltung aktiviert werden.');
        $email=null;if(in_array($action,['register','resend','forgot','login'],true))$email=self::email($b['email']??null);
        $hash=null;if(in_array($action,['verify','reset'],true))$hash=$this->password($b['password']??null);
        $this->cleanup();
        $this->db->beginTransaction();$message=null;
        try{
            $this->q('SELECT id FROM osmon_lock WHERE id=1 FOR UPDATE')->fetch();
            if($method==='GET'&&$action==='status'){
                $owner=(new FamilyStore($this->db,$this->clock))->parent($token);$a=$this->one('SELECT email,verified FROM osmon_accounts WHERE owner=?',[$owner]);$r=['account'=>$a?['email'=>$a['email'],'verified'=>(bool)$a['verified']]:null];
            }elseif($method==='POST'&&in_array($action,['register','resend','forgot'],true)){
                $a=$this->one('SELECT * FROM osmon_accounts WHERE email=?',[$email]);
                if($action==='register'&&!$a){
                    if($token!=='')$owner=(new FamilyStore($this->db,$this->clock))->parent($token);
                    else{$owner=tokenHash(newToken());$this->q('INSERT INTO osmon_parents(token,expires,public_id) VALUES (?,?,?)',[$owner,$this->now()+90*86400,newUUID()]);}
                    if(!$this->one('SELECT id FROM osmon_accounts WHERE owner=?',[$owner])){
                        $id=newUUID();$this->q('INSERT INTO osmon_accounts(id,email,owner,created) VALUES (?,?,?,?)',[$id,$email,$owner,$this->now()]);$a=$this->one('SELECT * FROM osmon_accounts WHERE id=?',[$id]);
                    }
                }
                if($a){if($action==='forgot'&&$a['verified'])$message=$this->mail($a,'reset');elseif($action!=='forgot'&&!$a['verified'])$message=$this->mail($a,'verify');}
                $r=['ok'=>true,'message'=>'Falls die Anfrage möglich ist, erhältst du eine E-Mail. Bitte auch Spam prüfen.'];
            }elseif($method==='POST'&&$action==='login'){
                $a=$this->one('SELECT a.*,p.suspended FROM osmon_accounts a JOIN osmon_parents p ON p.token=a.owner WHERE a.email=?',[$email]);
                $p=$b['password']??'';if(!is_string($p)||strlen($p)>72||str_contains($p,"\0"))$p='';
                $valid=password_verify($p,$a&&$a['password_hash']!==''?$a['password_hash']:'$2y$10$92IXUNpkjO0rOQ5byMi.Ye4oKoEa3Ro9llC/.og/at2uheWG/igi.');
                if(!$a||!$valid||!$a['verified']||$a['suspended']||(float)$a['locked_until']>$this->now()){
                    if($a&&(float)$a['locked_until']<=$this->now()){$n=(int)$a['failures']+1;$this->q('UPDATE osmon_accounts SET failures=?,locked_until=? WHERE id=?',[$n>=5?0:$n,$n>=5?$this->now()+900:0,$a['id']]);}
                    $r=['loginFailed'=>true];
                }else{$this->q('UPDATE osmon_accounts SET failures=0,locked_until=0 WHERE id=?',[$a['id']]);$r=$this->session($a['owner']);}
            }elseif($method==='POST'&&in_array($action,['verify','reset'],true)){
                $raw=$b['code']??'';if(!is_string($raw)||!preg_match('/^[A-Za-z0-9_-]{43}$/D',$raw))fail(400,'Link ungültig oder abgelaufen.');
                $a=$this->one('SELECT a.*,p.suspended FROM osmon_account_tokens t JOIN osmon_accounts a ON a.id=t.account_id JOIN osmon_parents p ON p.token=a.owner WHERE t.token=? AND t.purpose=? AND t.expires>?',[tokenHash($raw),$action,$this->now()]);
                if(!$a||$a['suspended']||($action==='reset'&&!$a['verified'])||($action==='verify'&&$a['verified']))fail(400,'Link ungültig oder abgelaufen.');
                $this->rekey($a);$this->q('UPDATE osmon_accounts SET password_hash=?,verified=1,failures=0,locked_until=0 WHERE id=?',[$hash,$a['id']]);
                $this->q('DELETE FROM osmon_account_tokens WHERE account_id=?',[$a['id']]);$r=['ok'=>true];
            }else fail(404,'Nicht gefunden.');
            $this->db->commit();
        }catch(Throwable $e){if($this->db->inTransaction())$this->db->rollBack();throw $e;}
        // No SMTP socket under database lock; generic response never discloses existing accounts.
        if($message){try{($this->deliver)(...$message);}catch(Throwable $e){/* Secrets and recipient are not logged. Test SMTP in /admin. */}}
        if(isset($r['loginFailed']))fail(401,'Anmeldung fehlgeschlagen. E-Mail, Passwort und Bestätigung prüfen oder später erneut versuchen.');return $r;
    }
}
