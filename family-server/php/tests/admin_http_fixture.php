<?php
if(PHP_SAPI!=='cli'||getenv('OSMON_TEST_DATABASE')!=='osmon_test')exit(1);
require dirname(__DIR__).'/private/AdminStore.php';
$c=['host'=>getenv('OSMON_TEST_HOST')?:'127.0.0.1','port'=>(int)(getenv('OSMON_TEST_PORT')?:3306),'database'=>'osmon_test','username'=>getenv('OSMON_TEST_USER')?:'root','password'=>getenv('OSMON_TEST_PASSWORD')?:'test-only'];
$db=connectFamily($c);$db->exec('DELETE FROM osmon_admin_sessions');$db->exec('DELETE FROM osmon_admins');
$now=microtime(true)-60;$secret='12345678901234567890';
$a=new AdminStore($db,str_repeat('k',32),static fn()=>$now);
$a->provision('httpoperator','Test-only-password-123',$secret,totpCode($secret,(int)floor($now/30)));
