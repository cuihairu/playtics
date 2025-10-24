#!/usr/bin/env python3
import sys, os, json

def extract(path):
    with open(path,'r') as f:
        j=json.load(f)
    m=j.get('metrics',{})
    d=m.get('http_req_duration',{})
    http_reqs=m.get('http_reqs',{}).get('count','')
    avg=d.get('avg','')
    med=d.get('med','')
    p95=d.get('p(95)','') or d.get('percentiles',{}).get('p(95)','')
    p99=d.get('p(99)','') or d.get('percentiles',{}).get('p(99)','')
    return http_reqs, avg, med, p95, p99

if __name__=='__main__':
    if len(sys.argv)<2:
        print('Usage: perf-report.py <dir>')
        sys.exit(1)
    print('file,http_reqs,avg_ms,med_ms,p95_ms,p99_ms')
    for fn in sorted(os.listdir(sys.argv[1])):
        if not fn.endswith('.json'): continue
        path=os.path.join(sys.argv[1],fn)
        http_reqs,avg,med,p95,p99=extract(path)
        print(f"{fn},{http_reqs},{avg},{med},{p95},{p99}")
