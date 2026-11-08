<?php
if(PHP_SAPI!=='cli'||getenv('OSMON_TEST_DATABASE')!=='osmon_test')exit(1);
require dirname(__DIR__).'/private/AccountStore.php';
$c=['host'=>getenv('OSMON_TEST_HOST')?:'127.0.0.1','port'=>(int)(getenv('OSMON_TEST_PORT')?:3306),'database'=>'osmon_test','username'=>getenv('OSMON_TEST_USER')?:'root','password'=>getenv('OSMON_TEST_PASSWORD')?:'test-only'];
$db=connectFamily($c);$db->exec('DELETE FROM osmon_parents');$db->exec('DELETE FROM osmon_rates');$a=new AccountStore($db,'https://parents.example.test',static function(){});$a->install();
$owner=tokenHash(newToken());$db->prepare('INSERT INTO osmon_parents(token,expires) VALUES (?,?)')->execute([$owner,4102444800]);
$db->prepare('INSERT INTO osmon_accounts(id,email,owner,password_hash,verified,created) VALUES (?,?,?,?,1,?)')->execute([newUUID(),'httpaccount@example.test',$owner,password_hash('Test-only-password-123',PASSWORD_DEFAULT),microtime(true)]);
