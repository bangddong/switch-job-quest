# -*- coding: utf-8 -*-
import io,re
# 아이콘 원본: https://aws.amazon.com/architecture/icons/ 의 Icon-package zip 을 풀어
# AWS_ICONS 에 그 경로를 준다.  예) AWS_ICONS=~/Downloads/pkg python3 aws-architecture-gen.py
import os
P=os.environ.get("AWS_ICONS","/tmp/arch/pkg"); S=f"{P}/Architecture-Service-Icons_07312026"
R=f"{P}/Resource-Icons_07312026"; G=f"{P}/Architecture-Group-Icons_07312026"
I={"s3":f"{S}/Arch_Storage/64/Arch_Amazon-Simple-Storage-Service_64.svg",
 "ddb":f"{S}/Arch_Databases/64/Arch_Amazon-DynamoDB_64.svg",
 "ecr":f"{S}/Arch_Containers/64/Arch_Amazon-Elastic-Container-Registry_64.svg",
 "ebs":f"{S}/Arch_Storage/64/Arch_Amazon-Elastic-Block-Store_64.svg",
 "acm":f"{S}/Arch_Security-Identity/64/Arch_AWS-Certificate-Manager_64.svg",
 "iam":f"{S}/Arch_Security-Identity/64/Arch_AWS-Identity-and-Access-Management_64.svg",
 "bud":f"{S}/Arch_Cloud-Financial-Management/64/Arch_AWS-Budgets_64.svg",
 "cost":f"{S}/Arch_Cloud-Financial-Management/64/Arch_AWS-Cost-Explorer_64.svg",
 "cw":f"{S}/Arch_Management-Tools/64/Arch_Amazon-CloudWatch_64.svg",
 "elb":f"{S}/Arch_Networking-Content-Delivery/64/Arch_Elastic-Load-Balancing_64.svg",
 "eks":f"{S}/Arch_Containers/64/Arch_Amazon-Elastic-Kubernetes-Service_64.svg",
 "ec2":f"{S}/Arch_Compute/64/Arch_Amazon-EC2_64.svg",
 "sm":f"{S}/Arch_Security-Identity/64/Arch_AWS-Secrets-Manager_64.svg",
 "rds":f"{S}/Arch_Databases/64/Arch_Amazon-RDS_64.svg",
 "igw":f"{R}/Res_Networking-Content-Delivery/Res_Amazon-VPC_Internet-Gateway_48.svg",
 "pod":f"{R}/Res_Containers/Res_Amazon-Elastic-Container-Service_Container-1_48.svg",
 "users":f"{R}/Res_General-Icons/Res_48_Light/Res_Users_48_Light.svg",
 "cloud":f"{G}/AWS-Cloud_32.svg","vpc":f"{G}/Virtual-private-cloud-VPC_32.svg",
 "pubsub":f"{G}/Public-subnet_32.svg"}
_c={}
def place(k,x,y,sz):
    if k not in _c:
        t=io.open(I[k],encoding="utf-8").read()
        t=re.sub(r'<\?xml.*?\?>','',t,flags=re.S); t=re.sub(r'<title>.*?</title>','',t,flags=re.S)
        m=re.search(r'viewBox="([^"]+)"',t); vb=m.group(1) if m else "0 0 80 80"
        b=re.sub(r'^.*?<svg[^>]*>','',t,flags=re.S); b=re.sub(r'</svg>\s*$','',b,flags=re.S)
        _c[k]=(vb,b)
    vb,b=_c[k]; return f'<svg x="{x}" y="{y}" width="{sz}" height="{sz}" viewBox="{vb}" overflow="visible">{b}</svg>'

W=H=1300
F="-apple-system,'Apple SD Gothic Neo','Pretendard','Noto Sans KR',system-ui,sans-serif"
M="ui-monospace,SFMono-Regular,Menlo,monospace"
o=[];e=o.append
def esc(t): return t.replace("&","&amp;").replace("<","&lt;").replace(">","&gt;")
def grp(x,y,w,h,label,color,badge=None,dash=None,fill="none"):
    d=f' stroke-dasharray="{dash}"' if dash else ""
    e(f'<rect x="{x}" y="{y}" width="{w}" height="{h}" rx="4" fill="{fill}" stroke="{color}" stroke-width="2"{d}/>')
    tx=x+12
    if badge: e(place(badge,x+9,y+9,20)); tx=x+35
    e(f'<text x="{tx}" y="{y+24}" font-size="12.5" font-weight="700" fill="{color}">{esc(label)}</text>')
def svc(k,cx,cy,lines,sz=50,mut=False,badge=None):
    op=0.42 if mut else 1
    e(f'<g opacity="{op}">{place(k,cx-sz/2,cy-sz/2,sz)}</g>')
    if badge:
        e(f'<rect x="{cx+sz/2-6}" y="{cy-sz/2-12}" width="44" height="16" rx="3" fill="#B0801F"/>')
        e(f'<text x="{cx+sz/2+16}" y="{cy-sz/2-1}" text-anchor="middle" font-family="{M}" font-size="9.5" font-weight="700" fill="#fff">{esc(badge)}</text>')
    yy=cy+sz/2+16
    for i,l in enumerate(lines):
        e(f'<text x="{cx}" y="{yy}" text-anchor="middle" font-family="{F if i==0 else M}" font-size="{11 if i==0 else 9.5}" '
          f'font-weight="{600 if i==0 else 400}" fill="{"#16191F" if i==0 else "#687787"}" opacity="{op}">{esc(l)}</text>'); yy+=13
def tile(cx,cy,col,gl,lines):
    e(f'<rect x="{cx-21}" y="{cy-21}" width="42" height="42" rx="6" fill="{col}"/>')
    e(f'<text x="{cx}" y="{cy+6}" text-anchor="middle" font-family="{M}" font-size="11" font-weight="700" fill="#fff">{esc(gl)}</text>')
    yy=cy+37
    for i,l in enumerate(lines):
        e(f'<text x="{cx}" y="{yy}" text-anchor="middle" font-family="{F if i==0 else M}" font-size="{11 if i==0 else 9.5}" '
          f'font-weight="{600 if i==0 else 400}" fill="{"#16191F" if i==0 else "#687787"}">{esc(l)}</text>'); yy+=13
def num(n,x,y):
    e(f'<circle cx="{x}" cy="{y}" r="10" fill="#16191F"/>')
    e(f'<text x="{x}" y="{y+4}" text-anchor="middle" font-family="{M}" font-size="10.5" font-weight="700" fill="#fff">{n}</text>')
def arr(x1,y1,x2,y2,col="#545B64",dash=None):
    d=f' stroke-dasharray="{dash}"' if dash else ""
    e(f'<line x1="{x1}" y1="{y1}" x2="{x2}" y2="{y2}" stroke="{col}" stroke-width="1.6"{d} marker-end="url(#a)"/>')
def poly(pts,col="#545B64",dash=None):
    d=f' stroke-dasharray="{dash}"' if dash else ""
    e(f'<polyline points="{" ".join(f"{a},{b}" for a,b in pts)}" fill="none" stroke="{col}" stroke-width="1.6"{d} marker-end="url(#a)"/>')

e(f'<svg xmlns="http://www.w3.org/2000/svg" xmlns:xlink="http://www.w3.org/1999/xlink" width="{W}" height="{H}" viewBox="0 0 {W} {H}" font-family="{F}">')
e('<defs><marker id="a" viewBox="0 0 10 10" refX="9" refY="5" markerWidth="6" markerHeight="6" orient="auto-start-reverse"><path d="M0,0 L10,5 L0,10 z" fill="#545B64"/></marker></defs>')
e(f'<rect width="{W}" height="{H}" fill="#fff"/>')
e('<text x="34" y="40" font-size="17" font-weight="700" fill="#16191F">switch-job-quest — AWS 아키텍처 (2026-09-28)</text>')

# 사용자 · 운영(AWS 밖)
e(place("users",36,116,42)); e(f'<text x="57" y="182" text-anchor="middle" font-size="11" font-weight="600" fill="#16191F">사용자</text>')
grp(112,72,600,212,"운영 — AWS 밖","#3B6FD4",dash="6 4",fill="#F7FAFE")
tile(196,142,"#F38020","DNS",["Cloudflare","quest.dhbang.co.kr"])
tile(324,142,"#000000","▲",["Vercel","React 프런트"])
tile(452,142,"#8B5CF6","fly",["Fly.io · nrt","512MB ×1 상시"])
tile(580,142,"#00B888","DB",["Neon","Postgres"])
tile(452,232,"#4B5563","API",["Resend · Judge0 · Anthropic"])
arr(82,142,170,142); num(1,126,142)
arr(222,142,298,142); num(2,260,142)
arr(350,142,426,142); num(3,388,142)
arr(478,142,554,142); num(4,516,142)
arr(452,164,452,206)

# 개발 머신
tile(830,142,"#4B5563","DEV",["개발 머신 · CI","OpenTofu · Actions"])
poly([(852,124),(940,124),(940,296)]); num(5,896,124)

# AWS Cloud
grp(34,296,1232,930,"AWS Cloud   ap-northeast-2","#232F3E",badge="cloud")
grp(60,344,1180,292,"0-bootstrap — 상시 존재 · 월 $1.14","#B0801F",dash="6 4",fill="#FFFCF2")
r1,r2=436,552
for i,(k,l1,l2,bd) in enumerate([("s3","S3 tfstate","3객체 · 47 KB",None),("ddb","DynamoDB","state 락",None),
    ("s3","S3 backups","2객체 · 47 KB",None),("ecr","ECR × 3","14개 · 2.28 GB","$0.23"),("ebs","EBS 10 GiB","available","$0.91")]):
    svc(k,158+i*230,r1,[l1,l2],badge=bd)
for i,(k,l1,l2) in enumerate([("acm","ACM 인증서","eks.quest…"),("iam","IAM OIDC","+ Actions Role"),
    ("bud","Budgets × 2","20단계"),("cost","Cost Anomaly","DAILY $5"),("cw","CloudWatch","/aws/lambda/test")]):
    svc(k,158+i*230,r2,[l1,l2])

grp(60,666,1180,536,"VPC 10.0.0.0/16   ·   tofu apply 때만 생성 · 지금 0개","#7AA116",badge="vpc",dash="6 4",fill="#FCFDF9")
svc("igw",172,752,["Internet GW"],44,mut=True)
svc("elb",372,752,["ALB (Ingress)","시간당 + LCU"],mut=True)
svc("sm",600,752,["Secrets Mgr × 3"],mut=True)
svc("eks",840,752,["EKS 컨트롤플레인","$0.10/h"],mut=True)
arr(200,742,344,742,"#B6BEC6","4 4")
grp(86,836,660,182,"퍼블릭 서브넷 · ap-northeast-2a   10.0.0.0/20  ← persistent_az","#1D8102",badge="pubsub",dash="5 4")
svc("ec2",240,942,["EKS 노드그룹","t4g.medium ×1~2"],mut=True)
svc("ebs",480,942,["static PV","→ 영속 EBS"],mut=True)
arr(272,942,444,942,"#B6BEC6","4 4")
grp(776,836,440,182,"퍼블릭 서브넷 · 2c   10.0.16.0/20","#1D8102",badge="pubsub",dash="5 4")
e(f'<text x="996" y="942" text-anchor="middle" font-size="11" fill="#A2ABB4">노드가 뜨지 않는다 (EBS 가 2a)</text>')
grp(86,1042,1130,152,"데이터 — in-cluster Postgres 가 기본 · RDS 는 db_mode=rds 일 때만","#527FFF",dash="5 4")
svc("pod",300,1122,["Postgres 파드"],mut=True)
svc("rds",700,1122,["RDS (선택)","월 +$18"],mut=True)
e(f'<text x="34" y="1272" font-family="{M}" font-size="9.5" fill="#B4BCC4">AWS Architecture Icons (07312026)</text>')
e('</svg>')
io.open(os.environ.get("OUT","aws-architecture.svg"),"w",encoding="utf-8").write("\n".join(o))
print("생성 완료")
