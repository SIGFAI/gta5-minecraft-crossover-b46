import subprocess, sys
from PIL import Image
f = sys.argv[1]; ts = [float(x) for x in sys.argv[2:]]
c = Image.new('RGB', (1280, 360 * ((len(ts) + 1) // 2)))
for i, t in enumerate(ts):
    subprocess.run(["ffmpeg", "-hide_banner", "-loglevel", "error", "-y", "-ss", str(t), "-i", f, "-frames:v", "1", "-vf", "scale=640:360", "C:/mod/work/b46_1205/gen/_f.png"])
    c.paste(Image.open("C:/mod/work/b46_1205/gen/_f.png"), ((i % 2) * 640, (i // 2) * 360))
c.save("C:/mod/work/b46_1205/gen/sheet.png")
