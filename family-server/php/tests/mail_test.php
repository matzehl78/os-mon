<?php
declare(strict_types=1);
require dirname(__DIR__).'/private/MailSettings.php';
$dir=sys_get_temp_dir().'/osmon-mail-'.bin2hex(random_bytes(6));mkdir($dir,0700);$key=random_bytes(32);$m=new MailSettings($dir,$key);
function yes(bool $v):void{if(!$v)throw new RuntimeException('Assertion failed');}
function bad(Closure $f,int $status=400):void{try{$f();}catch(FamilyAPIError $e){yes($e->status===$status);return;}throw new RuntimeException('Expected rejection');}
$c=['host'=>'smtp.example.com','port'=>465,'security'=>'tls','username'=>'noreply@example.com','password'=>'not-a-real-password','from'=>'noreply@example.com','support'=>'support@example.com'];
try{
 yes(!$m->view()['hasPassword']);$v=$m->save($c);yes($v['hasPassword']&&!isset($v['password']));
 yes(!str_contains(file_get_contents($dir.'/.mail-settings'),$c['password']));yes((fileperms($dir.'/.mail-settings')&0077)===0);
 $changed=$c;$changed['password']='';$changed['support']='help@example.com';$m->save($changed);yes($m->read()['password']===$c['password']);yes($m->view()['support']==='help@example.com');
 foreach([
 ['host'=>'127.0.0.1'],['host'=>'localhost'],['host'=>"smtp.example.com\r\n"],['port'=>25],['security'=>'none'],['from'=>"user@example.com\r\nBcc: victim@example.com"],['password'=>"bad\0secret"],['support'=>'invalid']
 ]as$change)bad(fn()=>MailSettings::validate(array_replace($c,$change)));
 yes(MailSettings::validate(array_replace($c,['port'=>587,'security'=>'starttls']))['port']===587);
 bad(fn()=>MailSettings::email("victim@example.com\r\n"));
 $sealed=file_get_contents($dir.'/.mail-settings');file_put_contents($dir.'/.mail-settings',substr($sealed,0,-4).'AAAA');
 try{$m->read();throw new LogicException('Tampering accepted');}catch(RuntimeException $e){yes(!$e instanceof LogicException);}
 file_put_contents($dir.'/.mail-settings',$sealed);
 // Test limiter reserves even failed attempts; no network is used by this check.
 file_put_contents($dir.'/.mail-test',(string)time());bad(fn()=>$m->test('owner@example.com'),429);
 echo "PASS encrypted storage, no secret response, preserve password, tamper detection, validation, test limiter\n";
}finally{foreach(glob($dir.'/{*,.*}',GLOB_BRACE)as$p)if(is_file($p))unlink($p);rmdir($dir);}
