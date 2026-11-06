<?php
declare(strict_types=1);
if(PHP_SAPI!=='cli'){http_response_code(404);exit;}
require_once __DIR__.'/SetupWizard.php';
try{
    $directory=dirname(getenv('OSMON_CONFIG_FILE')?:__DIR__.'/config.php');
    $origin=$argv[1]??'https://parents.ulmatti-studios.com';
    $code=(new SetupWizard($directory))->start($origin);
    echo "Einrichtungsassistent für 30 Minuten freigeschaltet.\n";
    echo "Öffne im eigenen Browser: ".$origin."/setup\n";
    echo "Einmaliger Einrichtungscode (nicht teilen): ".$code."\n";
    echo "Keine Geheimnisse in URLs. Nach Abschluss wird der Assistent gesperrt.\n";
}catch(Throwable$e){fwrite(STDERR,"Start fehlgeschlagen: ".$e->getMessage()."\n");exit(1);}
