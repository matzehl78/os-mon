<?php
declare(strict_types=1);
if (PHP_SAPI!=='cli') { http_response_code(404);exit; }
require_once __DIR__.'/bootstrap.php';
require_once __DIR__.'/AccountStore.php';
try { $c=familyConfig();$db=connectFamily($c);(new FamilyStore($db))->cleanup();$accounts=new AccountStore($db,$c['origin'],static function(){});if($accounts->ready())$accounts->cleanup();echo "Bereinigung abgeschlossen.\n"; }
catch (Throwable $e) { fwrite(STDERR,"Bereinigung fehlgeschlagen.\n");exit(1); }
