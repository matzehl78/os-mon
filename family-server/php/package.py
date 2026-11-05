"""Build a credential-free installation ZIP. Output path must be explicit."""
from pathlib import Path
import sys, zipfile
root=Path(__file__).resolve().parent
output=Path(sys.argv[1]).resolve()
with zipfile.ZipFile(output,'w',zipfile.ZIP_DEFLATED) as z:
    for p in sorted((root/'private').iterdir()):
        if p.is_file() and p.name!='config.php': z.write(p,'osmon-parents/private/'+p.name)
    for p in sorted((root/'public').iterdir()):
        if p.is_file(): z.write(p,'osmon-parents/public/'+p.name)
    for p in sorted((root.parent/'web').iterdir()):
        if p.is_file(): z.write(p,'osmon-parents/public/'+p.name)
    z.write(root/'README.md','osmon-parents/README.md')
print(output)
