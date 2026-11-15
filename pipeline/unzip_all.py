"""Resume-safe unzip: skips files already extracted with the right size. Leaves out __MACOSX/.DS_Store."""
import os, sys, zipfile

raw = sys.argv[1]
jobs = [(os.path.join(raw, "biodcase", "Development_data.zip"), os.path.join(raw, "biodcase"))]
jobs += [(os.path.join(raw, "humbugdb", f"humbugdb_neurips_2021_{i}.zip"), os.path.join(raw, "humbugdb", "audio"))
         for i in range(1, 5)]
for zp, dest in jobs:
    z = zipfile.ZipFile(zp)
    done = skipped = 0
    for info in z.infolist():
        n = info.filename
        if n.startswith("__MACOSX") or "DS_Store" in n or n.endswith("/"):
            continue
        target = os.path.join(dest, *n.split("/"))
        if os.path.exists(target) and os.path.getsize(target) == info.file_size:
            skipped += 1
            continue
        z.extract(info, dest)
        done += 1
    print(f"{os.path.basename(zp)}: extracted {done}, already there {skipped}", flush=True)
