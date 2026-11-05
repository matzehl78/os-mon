<?php
if(PHP_SAPI!=='cli'||getenv('OSMON_TEST_DATABASE')!=='osmon_test')exit(1);
require dirname(__DIR__).'/private/SetupWizard.php';
$c=['host'=>getenv('OSMON_TEST_HOST')?:'127.0.0.1','port'=>(int)(getenv('OSMON_TEST_PORT')?:3306),'database'=>'osmon_test','username'=>getenv('OSMON_TEST_USER')?:'root','password'=>getenv('OSMON_TEST_PASSWORD')?:'test-only'];
$db=connectFamily($c);$db->exec('CREATE DATABASE IF NOT EXISTS osmon_setup_http_test CHARACTER SET utf8mb4');$c['database']='osmon_setup_http_test';$db=connectFamily($c);$db->exec('SET FOREIGN_KEY_CHECKS=0');foreach($db->query('SHOW TABLES')->fetchAll(PDO::FETCH_COLUMN)as$t)$db->exec('DROP TABLE `'.str_replace('`','``',$t).'`');$db->exec('SET FOREIGN_KEY_CHECKS=1');
$code=(new SetupWizard($argv[1]))->start('https://parents.example.test');echo encodeJSON(['code'=>$code]);
