# usage: python tools/rec.py <scene n> <seconds> [prepsecs]  -> gen/sceneN.mp4 (GTA window 1080p + GTA and Minecraft audio)
import subprocess, sys, time, ctypes, os, json
from ctypes import wintypes
sys.path.insert(0, os.path.dirname(__file__))
from pids import pids
ctypes.windll.user32.SetProcessDPIAware()
n, secs = sys.argv[1], float(sys.argv[2])
prep = float(sys.argv[3]) if len(sys.argv) > 3 else 9
G = "C:/mod/work/b46_1205/gen"
trig = G + "/scene.txt"
def w(t): open(trig, "w").write(t)
PL = r"C:\mod\vendor\universal-modder\um\ps1\ProcLoopback.ps1"
w(f"prep {n}"); time.sleep(prep)
gta, mc, _ = pids()
loops = []
for name, pid in (("gta", gta), ("mc", mc)):
    f = f"{G}/{name}.raw".replace("/", "\\")
    p = subprocess.Popen(["powershell", "-NoProfile", "-ExecutionPolicy", "Bypass", "-File", PL, "-TargetPid", str(pid), "-Out", f],
                         stdin=subprocess.PIPE, stdout=subprocess.PIPE, text=True)
    p.stdout.readline(); loops.append((name, p, f, time.time()))
h = ctypes.windll.user32.FindWindowW("grcWindow", None)
r = wintypes.RECT(); ctypes.windll.user32.GetClientRect(h, ctypes.byref(r))
pt = wintypes.POINT(0, 0); ctypes.windll.user32.ClientToScreen(h, ctypes.byref(pt))
vid = f"{G}/video{n}.mp4"
cmd = ["ffmpeg", "-y", "-f", "gdigrab", "-framerate", "30", "-offset_x", str(pt.x), "-offset_y", str(pt.y),
       "-video_size", f"{r.right}x{r.bottom}", "-i", "desktop", "-t", str(secs),
       "-c:v", "libx264", "-preset", "veryfast", "-crf", "18", "-pix_fmt", "yuv420p", vid]
pr = subprocess.Popen(cmd, stderr=subprocess.DEVNULL)
tv = time.time()
time.sleep(1.0)
w(f"go {n}")
pr.wait()
for name, p, f, t0 in loops:
    try: p.stdin.write("\n"); p.stdin.flush()
    except Exception: pass
time.sleep(1.5)
for name, p, f, t0 in loops: p.terminate()
out = f"{G}/scene{n}.mp4"
ins, fl = ["-i", vid], []
for i, (name, p, f, t0) in enumerate(loops):
    off = max(0.0, tv - t0)
    ins += ["-f", "f32le", "-ar", "48000", "-ac", "2", "-ss", f"{off:.2f}", "-i", f]
mix = "[1:a][2:a]amix=inputs=2:duration=first:normalize=0,volume=1.0[a]"
subprocess.run(["ffmpeg", "-y", "-loglevel", "error"] + ins + ["-filter_complex", mix, "-map", "0:v", "-map", "[a]", "-t", str(secs),
               "-c:v", "copy", "-c:a", "aac", "-b:a", "192k", out])
print("saved", out)
