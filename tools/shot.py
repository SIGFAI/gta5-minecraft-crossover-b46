import ctypes, sys
from ctypes import wintypes
from PIL import ImageGrab
ctypes.windll.user32.SetProcessDPIAware()
h = ctypes.windll.user32.FindWindowW("grcWindow", None)
r = wintypes.RECT(); ctypes.windll.user32.GetClientRect(h, ctypes.byref(r))
p = wintypes.POINT(0, 0); ctypes.windll.user32.ClientToScreen(h, ctypes.byref(p))
print(p.x, p.y, r.right, r.bottom)
ImageGrab.grab(bbox=(p.x, p.y, p.x + r.right, p.y + r.bottom), all_screens=True).resize((960, 540)).save(sys.argv[1])
