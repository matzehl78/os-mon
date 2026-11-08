<?php
declare(strict_types=1);

final class FamilyAPIError extends RuntimeException {
    public function __construct(public int $status, string $message) { parent::__construct($message); }
}
function fail(int $status, string $message): never { throw new FamilyAPIError($status, $message); }
function tokenHash(string $raw): string { return hash('sha256', $raw); }
function newToken(): string { return rtrim(strtr(base64_encode(random_bytes(32)), '+/', '-_'), '='); }
function newCode(): string {
    $alphabet='ABCDEFGHJKLMNPQRSTUVWXYZ23456789'; $code='';
    for ($i=0;$i<10;$i++) $code.=$alphabet[random_int(0,strlen($alphabet)-1)];
    return $code;
}
function newUUID(): string {
    $b=random_bytes(16);$b[6]=chr((ord($b[6])&15)|64);$b[8]=chr((ord($b[8])&63)|128);
    $h=bin2hex($b);return substr($h,0,8).'-'.substr($h,8,4).'-'.substr($h,12,4).'-'.substr($h,16,4).'-'.substr($h,20);
}
function encodeJSON(mixed $value): string { return json_encode($value,JSON_UNESCAPED_UNICODE|JSON_THROW_ON_ERROR); }
function exactKeys(mixed $value, array $keys): bool {
    if (!is_array($value)) return false;
    $actual=array_keys($value);sort($actual);sort($keys);return $actual===$keys;
}
function normalizeCode(mixed $code): string {
    if (!is_string($code)) fail(400,'Ungültiger Code.');
    $code=strtoupper(str_replace([' ','-'],'',$code));
    if (strlen($code)!==10) fail(400,'Der Code hat 10 Zeichen.');return $code;
}
function validConfig(mixed $c): array {
    if (!exactKeys($c,['limit','tasks']) || !is_int($c['limit']) || $c['limit']<0 || $c['limit']>3) fail(400,'Ungültiges Spielelimit.');
    if (!is_array($c['tasks']) || !array_is_list($c['tasks']) || count($c['tasks'])>12) fail(400,'Höchstens 12 Aufgaben.');
    $ids=[];
    foreach ($c['tasks'] as $t) {
        if (!exactKeys($t,['id','title','repeat']) || !is_string($t['id']) || !preg_match('/^[a-fA-F0-9]{8}-[a-fA-F0-9]{4}-[a-fA-F0-9]{4}-[a-fA-F0-9]{4}-[a-fA-F0-9]{12}$/D',$t['id'])) fail(400,'Ungültige Aufgabe.');
        $id=strtolower($t['id']);if (isset($ids[$id])) fail(400,'Doppelte Aufgabe.');$ids[$id]=true;
        if (!is_string($t['title']) || mb_strlen(trim($t['title']))<1 || mb_strlen(trim($t['title']))>40 || preg_match('/[\x00-\x1f]/',$t['title'])) fail(400,'Aufgabentitel: 1 bis 40 Zeichen.');
        if (!in_array($t['repeat'],['once','daily','weekdays','weekend'],true)) fail(400,'Ungültige Wiederholung.');
    }
    return $c;
}
function connectFamily(array $c): PDO {
    return new PDO('mysql:host='.$c['host'].';port='.(int)$c['port'].';dbname='.$c['database'].';charset=utf8mb4',$c['username'],$c['password'],[
        PDO::ATTR_ERRMODE=>PDO::ERRMODE_EXCEPTION, PDO::ATTR_EMULATE_PREPARES=>false,
        PDO::ATTR_DEFAULT_FETCH_MODE=>PDO::FETCH_ASSOC, PDO::ATTR_STRINGIFY_FETCHES=>false,
    ]);
}
final class FamilyStore {
    private Closure $clock;
    public function __construct(public PDO $db, ?Closure $clock=null) { $this->clock=$clock ?? static fn()=>microtime(true); }
    public function now(): float { return (float)($this->clock)(); }
    private function query(string $sql, array $params=[]): PDOStatement { $s=$this->db->prepare($sql);$s->execute($params);return $s; }
    private function one(string $sql, array $params=[]): array|false { return $this->query($sql,$params)->fetch(); }
    private function locked(Closure $action): mixed {
        $this->db->beginTransaction();
        try {
            // Mirrors the Python MVP's serialized store; short transactions prevent claim/revoke races.
            if (!$this->one('SELECT id FROM osmon_lock WHERE id=1 FOR UPDATE')) fail(503,'Datenbankschema nicht eingerichtet.');
            $value=$action();$this->db->commit();return $value;
        } catch (Throwable $e) { if ($this->db->inTransaction()) $this->db->rollBack();throw $e; }
    }
    public function cleanup(): void {
        $this->locked(function(){
            $now=$this->now();
            $this->query('DELETE FROM osmon_devices WHERE owner IS NULL AND expires<=?',[$now]);
            $this->query('DELETE FROM osmon_parents WHERE expires<=?',[$now]);
            $this->query('DELETE FROM osmon_web_codes WHERE expires<=?',[$now]);
            $this->query('DELETE FROM osmon_web_sessions WHERE expires<=?',[$now]);
            $this->query('UPDATE osmon_devices SET location=NULL,location_at=NULL WHERE location_at<=?',[$now-86400]);
            $this->query('DELETE FROM osmon_admin_sessions WHERE expires<=? OR touched<=?',[$now,$now-1800]);
            $this->query('DELETE FROM osmon_admin_audit WHERE happened<?',[$now-90*86400]);
            $this->query('DELETE FROM osmon_rates WHERE minute<?',[(int)floor($now/60)-2]);
        });
    }
    public function rate(string $ip, string $route, string $secret): void {
        $limited=['/admin-api/login','/v1/account/register','/v1/account/resend','/v1/account/forgot','/v1/account/login','/v1/account/verify','/v1/account/reset','/web-api/account/register','/web-api/account/resend','/web-api/account/forgot','/web-api/account/login','/web-api/account/verify','/web-api/account/reset','/v1/parents','/v1/browser-access','/v1/pairings','/v1/claims','/v1/web-codes','/v1/web-codes/redeem','/web-api/access','/web-api/login','/web-api/claims'];
        $limit=($route==='/admin-api/login'||(in_array($route,$limited,true)&&str_contains($route,'/account/')))?5:(in_array($route,$limited,true)?10:120);
        // Bound key cardinality: unknown routes share one bucket, no raw IPs stored.
        $group=in_array($route,$limited,true)?$route:'other';
        $key=hash_hmac('sha256',$ip."\n".$group,$secret);$minute=(int)floor($this->now()/60);
        $this->query('INSERT INTO osmon_rates(bucket,minute,hits) VALUES (?,?,1) ON DUPLICATE KEY UPDATE hits=hits+1',[$key,$minute]);
        $row=$this->one('SELECT hits FROM osmon_rates WHERE bucket=? AND minute=?',[$key,$minute]);
        if ((int)$row['hits']>$limit) fail(429,'Zu viele Versuche. Bitte eine Minute warten.');
    }
    private function session(string $owner): string {
        $raw=newToken();$this->query('INSERT INTO osmon_web_sessions(token,owner,expires) VALUES (?,?,?)',[tokenHash($raw),$owner,$this->now()+604800]);return $raw;
    }
    public function parent(string $token): string {
        $key=tokenHash($token);$now=$this->now();
        if ($this->one('SELECT token FROM osmon_parents WHERE token=? AND expires>? AND suspended=0',[$key,$now])) return $key;
        $row=$this->one('SELECT s.owner FROM osmon_web_sessions s JOIN osmon_parents p ON p.token=s.owner WHERE s.token=? AND s.expires>? AND p.expires>? AND p.suspended=0',[$key,$now,$now]);
        if ($row) return $row['owner'];fail(401,'Elternzugang abgelaufen oder ungültig. Bitte erneut anmelden.');
    }
    private function device(string $token): array {
        $d=$this->one('SELECT * FROM osmon_devices WHERE token=?',[tokenHash($token)]);
        if (!$d || (!$d['owner'] && (float)$d['expires']<=$this->now())) fail(401,'Verbindung ungültig oder abgelaufen.');
        if ($d['owner'] && !$this->one('SELECT token FROM osmon_parents WHERE token=? AND expires>? AND suspended=0',[$d['owner'],$this->now()])) fail(401,'Elternzugang abgelaufen.');return $d;
    }
    private function request(array $d): ?array {
        $r=$d['location_request'] ? json_decode($d['location_request'],true,32,JSON_THROW_ON_ERROR):null;
        if ($r && $r['state']==='pending' && $r['expires']<=$this->now()) $r['state']='expired';return $r;
    }
    private function output(array $d): array {
        $loc=$d['location'] && (float)$d['location_at']>$this->now()-86400 ? json_decode($d['location'],true,32,JSON_THROW_ON_ERROR):null;
        return ['id'=>$d['id'],'name'=>$d['name'],'paired'=>(bool)$d['owner'],'config'=>json_decode($d['config'],true,32,JSON_THROW_ON_ERROR),
            'version'=>(int)$d['version'],'applied'=>(int)$d['applied'],'completed'=>json_decode($d['completed'],true,32,JSON_THROW_ON_ERROR),
            'seen'=>$d['seen']===null?null:(float)$d['seen'],'day'=>$d['status_day']===null?null:(int)$d['status_day'],
            'location'=>$loc,'locationSharing'=>(bool)$d['location_sharing'],'locationRequest'=>$this->request($d),
            'measuring'=>$this->measuring($d['id'])];
    }
    /** Screen time / steps measured at the child's last sync. Null for older apps or before the table exists. */
    private function measuring(string $id): ?array {
        try { $r=$this->one('SELECT screen,steps,updated FROM osmon_device_status WHERE device_id=?',[$id]); }
        catch (PDOException $e) { return null; }
        return $r ? ['screen'=>(bool)$r['screen'],'steps'=>(bool)$r['steps'],'updated'=>(float)$r['updated']] : null;
    }
    public const STATUS_TABLE='CREATE TABLE IF NOT EXISTS osmon_device_status (
 device_id CHAR(36) CHARACTER SET ascii COLLATE ascii_bin PRIMARY KEY,
 screen TINYINT NOT NULL, steps TINYINT NOT NULL, updated DOUBLE NOT NULL,
 CONSTRAINT osmon_status_device FOREIGN KEY(device_id) REFERENCES osmon_devices(id) ON DELETE CASCADE
) ENGINE=InnoDB';
    /** Hosted installs without SSH get the status table on first use. Runs outside the transaction: DDL commits implicitly. */
    public function ensureStatusTable(): void {
        try { if (!$this->db->query("SHOW TABLES LIKE 'osmon_device_status'")->fetch()) $this->db->exec(self::STATUS_TABLE); }
        catch (PDOException $e) { /* optional feature; acks keep working without it */ }
    }
    public function dispatch(string $method,string $path,string $token,array $body): array {
        if ($method==='POST' && $path==='/v1/device/ack' && isset($body['measuring'])) $this->ensureStatusTable();
        $this->cleanup(); // committed even when the following request is rejected
        return $this->locked(fn()=> $this->route($method,$path,$token,$body));
    }
    private function route(string $m,string $path,string $token,array $b): array {
        $now=$this->now();$ok=['ok'=>true];
        if ($m==='POST' && $path==='/v1/parents') {
            $raw=newToken();$this->query('INSERT INTO osmon_parents(token,expires) VALUES (?,?)',[tokenHash($raw),$now+90*86400]);return ['token'=>$raw];
        }
        if ($m==='POST' && $path==='/v1/browser-access') {
            $owner=tokenHash(newToken());$this->query('INSERT INTO osmon_parents(token,expires) VALUES (?,?)',[$owner,$now+90*86400]);return ['token'=>$this->session($owner)];
        }
        if ($m==='DELETE' && $path==='/v1/browser-session') { $this->query('DELETE FROM osmon_web_sessions WHERE token=?',[tokenHash($token)]);return $ok; }
        if ($m==='POST' && $path==='/v1/web-codes') {
            $owner=$this->parent($token);$code=newCode();$this->query('DELETE FROM osmon_web_codes WHERE owner=?',[$owner]);
            $this->query('INSERT INTO osmon_web_codes(code,owner,expires) VALUES (?,?,?)',[tokenHash($code),$owner,$now+300]);return ['code'=>$code,'expires'=>(int)($now+300)];
        }
        if ($m==='POST' && $path==='/v1/web-codes/redeem') {
            $code=normalizeCode($b['code']??'');
            $row=$this->one('SELECT c.owner FROM osmon_web_codes c JOIN osmon_parents p ON p.token=c.owner WHERE c.code=? AND c.expires>? AND p.expires>? AND p.suspended=0',[tokenHash($code),$now,$now]);
            if (!$row) fail(404,'Anmeldecode ungültig, abgelaufen oder bereits verwendet.');
            $this->query('DELETE FROM osmon_web_codes WHERE code=?',[tokenHash($code)]);return ['token'=>$this->session($row['owner'])];
        }
        if ($m==='POST' && $path==='/v1/pairings') {
            $name=$b['name']??null;
            if (!is_string($name) || mb_strlen(trim($name))<1 || mb_strlen(trim($name))>40 || preg_match('/[\x00-\x1f]/',$name)) fail(400,'Gerätename: 1 bis 40 Zeichen.');
            $config=validConfig($b['config']??null);$raw=newToken();$code=newCode();
            $this->query('INSERT INTO osmon_devices(id,token,name,code,expires,config,completed) VALUES (?,?,?,?,?,?,?)',[newUUID(),tokenHash($raw),trim($name),tokenHash($code),$now+600,encodeJSON($config),'[]']);
            return ['token'=>$raw,'code'=>$code,'expires'=>(int)($now+600)];
        }
        if ($m==='POST' && $path==='/v1/claims') {
            $owner=$this->parent($token);$code=normalizeCode($b['code']??'');
            $row=$this->one('SELECT count(*) AS n FROM osmon_devices WHERE owner=?',[$owner]);if ((int)$row['n']>=10) fail(400,'Höchstens zehn Geräte pro Zugang.');
            $d=$this->one('SELECT id FROM osmon_devices WHERE code=? AND owner IS NULL AND expires>?',[tokenHash($code),$now]);if (!$d) fail(404,'Code ungültig, benutzt oder abgelaufen.');
            $this->query('UPDATE osmon_devices SET owner=?,code=NULL,expires=NULL WHERE id=?',[$owner,$d['id']]);return $ok;
        }
        if ($m==='GET' && $path==='/v1/children') {
            $owner=$this->parent($token);$rows=$this->query('SELECT * FROM osmon_devices WHERE owner=? ORDER BY name',[$owner])->fetchAll();return ['children'=>array_map(fn($d)=>$this->output($d),$rows)];
        }
        if (preg_match('~^/v1/children/([a-fA-F0-9-]{36})(?:/(config|location-request))?$~D',$path,$parts)) {
            $owner=$this->parent($token);$id=$parts[1];$sub=$parts[2]??'';
            $d=$this->one('SELECT * FROM osmon_devices WHERE id=? AND owner=?',[$id,$owner]);if (!$d) fail(404,'Gerät nicht gefunden.');
            if ($m==='DELETE' && $sub==='') { $this->query('DELETE FROM osmon_devices WHERE id=?',[$id]);return $ok; }
            if ($m==='PUT' && $sub==='config') {
                $config=validConfig($b['config']??null);
                if (!is_int($b['version']??null) || $b['version']!==(int)$d['version']) fail(409,'Einstellungen zwischenzeitlich geändert. Bitte neu laden.');
                $this->query('UPDATE osmon_devices SET config=?,version=version+1 WHERE id=?',[encodeJSON($config),$id]);return $ok;
            }
            if ($m==='POST' && $sub==='location-request') {
                if (!$d['location_sharing']) fail(409,'Standortanfragen am Kindergerät nicht freigegeben.');
                $old=$this->request($d);if ($old && $old['state']==='pending') return ['request'=>$old];
                if ($old && $now-$old['created']<30) fail(429,'Bitte 30 Sekunden zwischen Anfragen warten.');
                $r=['id'=>newUUID(),'created'=>$now,'expires'=>$now+90,'state'=>'pending'];$this->query('UPDATE osmon_devices SET location_request=? WHERE id=?',[encodeJSON($r),$id]);return ['request'=>$r];
            }
        }
        if ($path==='/v1/device/location-sharing' && $m==='PUT') {
            $d=$this->device($token);if (!$d['owner']) fail(409,'Zuerst koppeln.');
            if (!exactKeys($b,['enabled']) || !is_bool($b['enabled'])) fail(400,'Ungültige Freigabe.');
            if ($b['enabled']) $this->query('UPDATE osmon_devices SET location_sharing=1 WHERE id=?',[$d['id']]);
            else $this->query('UPDATE osmon_devices SET location_sharing=0,location=NULL,location_at=NULL,location_request=NULL WHERE id=?',[$d['id']]);return $ok;
        }
        if ($path==='/v1/device/location-result' && $m==='POST') {
            $d=$this->device($token);$r=$this->request($d);
            if (!$d['location_sharing'] || !$r || $r['state']!=='pending' || $r['id']!==($b['requestId']??null)) fail(409,'Standortanfrage nicht mehr gültig.');
            if (!exactKeys($b,['requestId','state']) || !in_array($b['state'],['unavailable','permissionDenied'],true)) fail(400,'Ungültiger Standortstatus.');
            $r['state']=$b['state'];$this->query('UPDATE osmon_devices SET location_request=? WHERE id=?',[encodeJSON($r),$d['id']]);return $ok;
        }
        if ($path==='/v1/device/location') {
            $d=$this->device($token);if (!$d['owner']) fail(409,'Zuerst koppeln.');
            if ($m==='DELETE') { $this->query('UPDATE osmon_devices SET location=NULL,location_at=NULL,location_request=NULL WHERE id=?',[$d['id']]);return $ok; }
            if ($m==='POST') {
                $keys=['latitude','longitude','accuracy','measured'];
                if (!exactKeys($b,$keys) && !exactKeys($b,array_merge($keys,['requestId']))) fail(400,'Ungültiger Standort.');
                $r=null;
                if (array_key_exists('requestId',$b)) {
                    $r=$this->request($d);if (!$d['location_sharing'] || !$r || $r['state']!=='pending' || $r['id']!==$b['requestId']) fail(409,'Standortanfrage nicht mehr gültig.');
                }
                foreach ($keys as $k) if ((!is_int($b[$k]) && !is_float($b[$k])) || !is_finite((float)$b[$k])) fail(400,'Ungültiger Standort.');
                if (abs($b['latitude'])>90 || abs($b['longitude'])>180 || $b['accuracy']<0 || $b['accuracy']>100000 || $b['measured']<$now-120 || $b['measured']>$now+30) fail(400,'Standort ungültig oder älter als zwei Minuten.');
                if ($r && $b['measured']<$r['created']-5) fail(400,'Messung liegt vor der Anfrage.');
                $loc=array_intersect_key($b,array_flip($keys));$loc['received']=$now;
                if ($r) { $r['state']='received';$loc['requestId']=$r['id'];$this->query('UPDATE osmon_devices SET location_request=? WHERE id=?',[encodeJSON($r),$d['id']]); }
                $this->query('UPDATE osmon_devices SET location=?,location_at=? WHERE id=?',[encodeJSON($loc),$now,$d['id']]);return $ok;
            }
        }
        if ($path==='/v1/device' || $path==='/v1/device/ack') {
            $d=$this->device($token);
            if ($m==='GET' && $path==='/v1/device') return $this->output($d);
            if ($m==='DELETE' && $path==='/v1/device') { $this->query('DELETE FROM osmon_devices WHERE id=?',[$d['id']]);return $ok; }
            if ($m==='POST' && $path==='/v1/device/ack') {
                if (!$d['owner']) fail(409,'Noch nicht gekoppelt.');
                if (!is_int($b['version']??null) || $b['version']!==(int)$d['version']) fail(409,'Bitte aktuelle Einstellungen laden.');
                $done=$b['completed']??null;$day=$b['day']??null;$allowed=array_column(json_decode($d['config'],true)['tasks'],'id');
                if (!is_array($done) || !array_is_list($done) || count($done)>12 || !is_int($day) || $day< -2147483648 || $day>2147483647) fail(400,'Ungültiger Status.');
                foreach ($done as $id) if (!is_string($id) || !in_array($id,$allowed,true)) fail(400,'Ungültiger Status.');
                $measuring=$b['measuring']??null; // optional: older apps do not send it
                if ($measuring!==null && (!exactKeys($measuring,['screen','steps']) || !is_bool($measuring['screen']) || !is_bool($measuring['steps']))) fail(400,'Ungültiger Status.');
                $done=array_values(array_unique($done));sort($done);
                $this->query('UPDATE osmon_devices SET applied=?,completed=?,seen=?,status_day=? WHERE id=?',[(int)$d['version'],encodeJSON($done),$now,$day,$d['id']]);
                if ($measuring!==null) {
                    try { $this->query('INSERT INTO osmon_device_status(device_id,screen,steps,updated) VALUES (?,?,?,?) ON DUPLICATE KEY UPDATE screen=VALUES(screen),steps=VALUES(steps),updated=VALUES(updated)',[$d['id'],(int)$measuring['screen'],(int)$measuring['steps'],$now]); }
                    catch (PDOException $e) { /* status table unavailable: the ack itself still counts */ }
                }
                return $ok;
            }
        }
        if ($m==='DELETE' && $path==='/v1/parents') { $owner=$this->parent($token);$this->query('DELETE FROM osmon_parents WHERE token=?',[$owner]);return $ok; }
        fail(404,'Nicht gefunden.');
    }
}
