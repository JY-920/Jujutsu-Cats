"""Read-only snapshot of official NetEase API pages for the port."""
import concurrent.futures
from html.parser import HTMLParser
from pathlib import Path
from urllib.parse import quote
from urllib.request import urlopen

ROOT = Path(__file__).resolve().parents[1] / "build" / "reference"
PAGES = {
    "item-events": "mcdocs/1-ModAPI/事件/物品.html",
    "world-events": "mcdocs/1-ModAPI/事件/世界.html",
    "post-api": "mcdocs/1-ModAPI/接口/后处理/自定义.html",
    "render-api": "mcdocs/1-ModAPI/接口/实体/渲染.html",
    "mod-systems": "mcguide/20-玩法开发/13-模组SDK编程/2-Python脚本开发/4-系统简介.html",
}

class Text(HTMLParser):
    def __init__(self):
        super().__init__()
        self.parts = []
    def handle_starttag(self, tag, attrs):
        if tag in ("div", "p", "h1", "h2", "h3", "h4", "li", "tr", "br", "pre"):
            self.parts.append("\n")
    def handle_data(self, text):
        self.parts.append(text)

def fetch(entry):
    key, path = entry
    url = "https://mc.163.com/dev/mcmanual/mc-dev/" + quote(path, safe="/")
    try:
        raw = urlopen(url, timeout=25).read().decode("utf-8")
        start = raw.find('<div class="theme-default-content')
        parser = Text()
        parser.feed(raw[start:] if start >= 0 else raw)
        text = "\n".join(line.strip() for line in "".join(parser.parts).splitlines() if line.strip())
        (ROOT / (key + ".txt")).write_text(url + "\n" + text, encoding="utf-8")
        return key, len(text)
    except Exception as ex:
        return key, str(ex)

if __name__ == "__main__":
    ROOT.mkdir(parents=True, exist_ok=True)
    with concurrent.futures.ThreadPoolExecutor(max_workers=5) as pool:
        for result in pool.map(fetch, PAGES.items()):
            print(result)
