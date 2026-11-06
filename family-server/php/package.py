"""Build a credential-free installation ZIP. Output path must be explicit."""
from pathlib import Path
import sys, zipfile
root=Path(__file__).resolve().parent
output=Path(sys.argv[1]).resolve()
with zipfile.ZipFile(output,'w',zipfile.ZIP_DEFLATED) as z:
    private_files=['.htaccess','FamilyStore.php','AdminStore.php','MailSettings.php','bootstrap.php','config.example.php','schema.sql','install.php','cleanup.php','admin-setup.php','SetupWizard.php','SetupHTTP.php','setup-start.php','setup-enable.example.txt']
    for name in private_files:
        p=root/'private'/name
        z.write(p,'osmon-parents/private/'+name)
    for p in sorted((root/'public').iterdir()):
        if p.is_file(): z.write(p,'osmon-parents/public/'+p.name)
    for p in sorted((root.parent/'web').iterdir()):
        if p.is_file(): z.write(p,'osmon-parents/public/'+p.name)
    z.write(root/'README.md','osmon-parents/README.md')
print(output)
