import subprocess
def pids():
    ps = "Get-CimInstance Win32_Process | Where-Object { $_.Name -in 'java.exe','GTA5.exe' } | ForEach-Object { '{0}|{1}|{2}' -f $_.ProcessId,$_.Name,($_.CommandLine -replace ''\s+'',' ') }"
    out = subprocess.run(["powershell", "-NoProfile", "-Command", ps], capture_output=True, text=True).stdout
    gta = mc = None
    for l in out.splitlines():
        p = l.split("|", 2)
        if len(p) < 3: continue
        if p[1] == "GTA5.exe": gta = int(p[0])
        elif "fabric" in p[2].lower() and ("Client" in p[2] or "knot" in p[2].lower()) and "gradle" not in p[2].lower().split("knot")[0][-40:]: mc = int(p[0])
    return gta, mc, out
if __name__ == "__main__":
    g, m, out = pids(); print(g, m); print(out[:1500])
