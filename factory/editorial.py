"""Editorial CLI. Uses real Worker API; never manufactures publication receipts.
Author a JSON article with a source-reading agent, inspect exact content, then
obtain explicit human approval before running approve/publish.
"""
import argparse
import json
import os
from pathlib import Path
import requests


def main():
    p=argparse.ArgumentParser(description=__doc__)
    p.add_argument('action',choices=['draft','show','list','approve','publish','withdraw'])
    p.add_argument('--file',type=Path)
    p.add_argument('--slug')
    p.add_argument('--digest')
    p.add_argument('--reviewer')
    p.add_argument('--facts-checked',action='store_true')
    p.add_argument('--rights-checked',action='store_true')
    p.add_argument('--product-claims-checked',action='store_true')
    a=p.parse_args()
    base=os.environ.get('WORKER_URL','https://connectorlab-core.morfnegoden.workers.dev').rstrip('/')
    token=os.environ.get('WORKER_TOKEN')
    if not token: p.error('WORKER_TOKEN is required for editorial administration; GitHub factory OIDC cannot approve content')
    headers={'Authorization':'Bearer '+token}
    if a.action=='list': method,path,data='GET','/admin/articles',None
    elif a.action=='show': method,path,data='GET','/admin/article',None
    elif a.action=='draft':
        if not a.file: p.error('--file is required')
        method,path,data='POST','/admin/article',json.loads(a.file.read_text())
    else:
        if not a.slug or not a.digest: p.error('--slug and --digest are required; read exact content first')
        method,path='POST',{'approve':'/admin/article/review','publish':'/admin/article/publish','withdraw':'/admin/article/unpublish'}[a.action]
        data={'slug':a.slug,'digest':a.digest}
        if a.action=='approve':
            if not (a.reviewer and a.facts_checked and a.rights_checked and a.product_claims_checked):
                p.error('approve requires a reviewer and all three explicit editorial checks')
            data.update(decision='approve',reviewer=a.reviewer,facts_checked=True,rights_checked=True,product_claims_checked=True)
    r=requests.request(method,base+path,headers=headers,json=data,params={'slug':a.slug} if a.action=='show' else None,timeout=30)
    if r.status_code>=400: raise SystemExit(f'Editorial request failed: HTTP {r.status_code}: {r.text[:250]}')
    print(json.dumps(r.json(),ensure_ascii=False,indent=2))

if __name__=='__main__':main()
