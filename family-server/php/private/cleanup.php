<?php
declare(strict_types=1);
if (PHP_SAPI!=='cli') { http_response_code(404);exit; }
require_once __DIR__.'/bootstrap.php';
try { (new FamilyStore(connectFamily(familyConfig())))->cleanup();echo "Bereinigung abgeschlossen.\n"; }
catch (Throwable $e) { fwrite(STDERR,"Bereinigung fehlgeschlagen.\n");exit(1); }
