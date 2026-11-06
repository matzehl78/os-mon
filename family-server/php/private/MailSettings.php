<?php
declare(strict_types=1);
require_once __DIR__.'/AdminStore.php';

/** Private encrypted settings; no schema migration or config.php overwrite. */
final class MailSettings {
    public function __construct(private string $directory, private string $key) {}
    public function read(): array {
        $p=$this->directory.'/.mail-settings';
        if(!is_file($p))return [];
        return json_decode(decryptAdminSecret((string)file_get_contents($p),$this->key,'smtp-v1'),true,16,JSON_THROW_ON_ERROR);
    }
    public function view(): array {
        $c=$this->read();$has=isset($c['password']);unset($c['password']);
        return $c+['host'=>'','port'=>465,'security'=>'tls','username'=>'','from'=>'noreply@ulmatti-studios.com','support'=>'support@ulmatti-studios.com','hasPassword'=>$has];
    }
    public static function email(mixed $v): string {
        if(!is_string($v)||strlen($v)>254||preg_match('/[\r\n\x00-\x20\x7f]/',$v)||!filter_var($v,FILTER_VALIDATE_EMAIL))fail(400,'Gültige E-Mail-Adresse eingeben.');
        return $v;
    }
    public static function validate(array $c): array {
        $h=$c['host']??'';$port=$c['port']??null;$security=$c['security']??'';
        if(!is_string($h)||strlen($h)>253||!str_contains($h,'.')||!filter_var($h,FILTER_VALIDATE_DOMAIN,FILTER_FLAG_HOSTNAME)||filter_var($h,FILTER_VALIDATE_IP))fail(400,'Öffentlichen SMTP-Servernamen eingeben.');
        if(!in_array([$port,$security],[[465,'tls'],[587,'starttls']],true))fail(400,'SMTP: Port 465 mit TLS oder 587 mit STARTTLS wählen.');
        foreach(['username','password']as$k)if(!isset($c[$k])||!is_string($c[$k])||$c[$k]===''||strlen($c[$k])>1024||preg_match('/[\r\n\x00]/',$c[$k]))fail(400,'SMTP-Benutzer und Passwort erforderlich.');
        return ['host'=>strtolower($h),'port'=>$port,'security'=>$security,'username'=>$c['username'],'password'=>$c['password'],'from'=>self::email($c['from']??null),'support'=>self::email($c['support']??null)];
    }
    public function save(array $body): array {
        $lock=fopen($this->directory.'/.mail-lock','c');if(!$lock||!flock($lock,LOCK_EX))fail(503,'E-Mail-Einstellungen nicht beschreibbar.');
        @chmod($this->directory.'/.mail-lock',0600);$tmp=false;
        try{
            if(($body['password']??'')==='')$body['password']=$this->read()['password']??'';
            $c=self::validate($body);$sealed=encryptAdminSecret(encodeJSON($c),$this->key,'smtp-v1');
            $tmp=tempnam($this->directory,'.mail-');if($tmp===false||!chmod($tmp,0600)||file_put_contents($tmp,$sealed)!==strlen($sealed)||!rename($tmp,$this->directory.'/.mail-settings'))fail(503,'E-Mail-Einstellungen konnten nicht gespeichert werden.');
            return $this->view();
        }finally{if($tmp&&is_file($tmp))unlink($tmp);flock($lock,LOCK_UN);fclose($lock);}
    }
    /** Reserve before attempting network I/O; concurrent requests cannot flood SMTP. */
    public function test(string $recipient): void {
        self::email($recipient);$c=self::validate($this->read());
        $p=$this->directory.'/.mail-test';$f=fopen($p,'c+');if(!$f||!flock($f,LOCK_EX))fail(503,'Testversand nicht verfügbar.');
        try{
            chmod($p,0600);$last=(int)stream_get_contents($f);if(time()-$last<60)fail(429,'Bitte vor dem nächsten Testversand eine Minute warten.');
            rewind($f);ftruncate($f,0);fwrite($f,(string)time());fflush($f);
        }finally{flock($f,LOCK_UN);fclose($f);}
        (new SMTPMailer())->sendTest($c,$recipient);
    }
}

final class SMTPMailer {
    private function reply($s,array $allowed): string {
        $result='';$expected=null;
        for($i=0;$i<100;$i++){
            $line=fgets($s,2048);if($line===false||!str_ends_with($line,"\r\n")||!preg_match('/^([0-9]{3})([ -])/',$line,$m))throw new RuntimeException('SMTP protocol');
            if($expected!==null&&$expected!==$m[1])throw new RuntimeException('SMTP protocol');$expected=$m[1];$result.=$line;
            if($m[2]===' '){if(!in_array((int)$m[1],$allowed,true))throw new RuntimeException('SMTP rejected');return $result;}
        }throw new RuntimeException('SMTP response limit');
    }
    private function write($s,string $v): void {
        while($v!==''){$n=fwrite($s,$v);if($n===false||$n===0)throw new RuntimeException('SMTP write');$v=substr($v,$n);}
    }
    private function command($s,string $v,array $allowed): string {$this->write($s,$v."\r\n");return $this->reply($s,$allowed);}
    public function sendTest(array $c,string $recipient): void {
        $c=MailSettings::validate($c);MailSettings::email($recipient);$s=null;
        try{
            // Resolve once, reject all non-public addresses and pin the chosen IP.
            $records=dns_get_record($c['host'],DNS_A|DNS_AAAA);$ips=[];
            foreach($records?:[]as$r){$ip=$r['ip']??$r['ipv6']??null;if($ip===null)continue;
                if(!filter_var($ip,FILTER_VALIDATE_IP,FILTER_FLAG_NO_PRIV_RANGE|FILTER_FLAG_NO_RES_RANGE)||str_starts_with(strtolower($ip),'::ffff:'))throw new RuntimeException('Non-public SMTP address');$ips[]=$ip;}
            if(!$ips)throw new RuntimeException('SMTP DNS');$ip=$ips[0];$address=str_contains($ip,':')?'['.$ip.']':$ip;
            $ctx=stream_context_create(['ssl'=>['verify_peer'=>true,'verify_peer_name'=>true,'peer_name'=>$c['host'],'allow_self_signed'=>false,'SNI_enabled'=>true,'crypto_method'=>STREAM_CRYPTO_METHOD_TLSv1_2_CLIENT|STREAM_CRYPTO_METHOD_TLSv1_3_CLIENT]]);
            $s=@stream_socket_client(($c['security']==='tls'?'tls':'tcp').'://'.$address.':'.$c['port'],$errno,$err,10,STREAM_CLIENT_CONNECT,$ctx);
            if(!$s)throw new RuntimeException('SMTP connect');stream_set_timeout($s,5);$this->reply($s,[220]);
            $cap=$this->command($s,'EHLO parents.ulmatti-studios.com',[250]);
            if($c['security']==='starttls'){
                if(!preg_match('/^250[ -]STARTTLS\b/im',$cap))throw new RuntimeException('STARTTLS unavailable');
                $this->command($s,'STARTTLS',[220]);if(@stream_socket_enable_crypto($s,true,STREAM_CRYPTO_METHOD_TLSv1_2_CLIENT|STREAM_CRYPTO_METHOD_TLSv1_3_CLIENT)!==true)throw new RuntimeException('TLS failed');
                $cap=$this->command($s,'EHLO parents.ulmatti-studios.com',[250]);
            }
            if(preg_match('/^250[ -]AUTH[^\r\n]*\bPLAIN\b/im',$cap))$this->command($s,'AUTH PLAIN '.base64_encode("\0".$c['username']."\0".$c['password']),[235]);
            elseif(preg_match('/^250[ -]AUTH[^\r\n]*\bLOGIN\b/im',$cap)){
                $this->command($s,'AUTH LOGIN',[334]);$this->command($s,base64_encode($c['username']),[334]);$this->command($s,base64_encode($c['password']),[235]);
            }else throw new RuntimeException('SMTP AUTH unavailable');
            $this->command($s,'MAIL FROM:<'.$c['from'].'>',[250]);$this->command($s,'RCPT TO:<'.$recipient.'>',[250,251]);$this->command($s,'DATA',[354]);
            $message='Date: '.gmdate('D, d M Y H:i:s').' +0000'."\r\n".'Message-ID: <'.bin2hex(random_bytes(16)).'@'.substr(strrchr($c['from'],'@'),1).">\r\n".'From: OS-Mon <'.$c['from'].">\r\nTo: <".$recipient.">\r\nReply-To: ".$c['support']."\r\nSubject: OS-Mon SMTP-Test\r\nMIME-Version: 1.0\r\nContent-Type: text/plain; charset=UTF-8\r\nContent-Transfer-Encoding: base64\r\n\r\n".chunk_split(base64_encode("Der E-Mail-Versand deiner OS-Mon-Plattform funktioniert.\nDies ist eine manuell ausgelöste Testnachricht.\n"),76,"\r\n");
            $this->write($s,$message.".\r\n");$this->reply($s,[250]);
            // Accepted DATA is success even if the connection closes before QUIT.
            @fwrite($s,"QUIT\r\n");
        }catch(Throwable $e){fail(503,'SMTP-Test fehlgeschlagen. Servername, Port, Zugangsdaten und ausgehende SMTP-Verbindungen beim Hoster prüfen.');}
        finally{if(is_resource($s))fclose($s);}
    }
}
