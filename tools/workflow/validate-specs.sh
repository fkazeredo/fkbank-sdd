#!/usr/bin/env bash
set -euo pipefail
python3 - <<'PY'
import glob,os,re,sys
files=glob.glob('docs/specs/SPEC-[0-9][0-9][0-9][0-9]-*.md'); known={}; parsed={}; failed=[]
_mf='docs/DOMAIN-MODULES.txt'; allowed_modules=({l.split('#')[0].strip() for l in open(_mf,encoding='utf-8')}-{''}) if os.path.exists(_mf) else set()
for path in files:
 text=open(path,encoding='utf-8').read(); m=re.match(r'^---\n(.*?)\n---',text,re.S)
 if not m: failed.append(f'{path}: missing frontmatter'); continue
 fm=m.group(1); mid=re.search(r'^id:\s*(SPEC-\d{4})\s*$',fm,re.M)
 if not mid: failed.append(f'{path}: invalid id'); continue
 known[mid.group(1)]=path; parsed[path]=fm
 for key in ('title','status','risk','profile','modules','depends_on','relevant_adrs','reading_list','planned_sprint','planned_release','owner_approved_at','owner_approved_hash'):
  if not re.search(rf'^{key}:\s*',fm,re.M): failed.append(f'{path}: missing {key}')
 if re.search(r'^status:\s*READY\s*$',fm,re.M) and re.search(r'^owner_approved_at:\s*null\s*$',fm,re.M): failed.append(f'{path}: READY without owner approval')
 if re.search(r'^status:\s*(READY|IN_PROGRESS|IMPLEMENTED)\s*$',fm,re.M) and re.search(r'^owner_approved_hash:\s*null\s*$',fm,re.M): failed.append(f'{path}: approved lifecycle state without content hash')
 if re.search(r'SPEC-\d{4}\.\.',fm): failed.append(f'{path}: dependency range not expanded')
 sf=re.search(r'^split_from:\s*(\S+)\s*$',fm,re.M)
 if sf and not re.match(r'^SPEC-\d{4}$',sf.group(1)): failed.append(f'{path}: invalid split_from')
 for section in ('Business rules','Decision log'):
  if not re.search(rf'^## {re.escape(section)}\s*$',text,re.M): failed.append(f'{path}: missing ## {section}')
 line=re.search(r'^modules:\s*\[(.*?)\]\s*$',fm,re.M)
 for module in [x.strip().strip('"\'') for x in (line.group(1).split(',') if line else []) if x.strip()]:
  if allowed_modules and module not in allowed_modules: failed.append(f'{path}: unknown module {module}')
for path,fm in parsed.items():
 line=re.search(r'^depends_on:\s*\[(.*?)\]',fm,re.M)
 for dep in re.findall(r'SPEC-\d{4}',line.group(1) if line else ''):
  if dep not in known: failed.append(f'{path}: dependency {dep} does not exist')
if failed: print(*failed,sep='\n',file=sys.stderr);sys.exit(1)
print(f'validate-specs: PASS ({len(files)} specs)')
PY
