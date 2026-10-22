"""Woolly sheep for offline renders: the renderer drops RandomAttachmentSets, so this grafts the vanilla
Wool_Attachment pieces onto the sheep model and packs both textures into one atlas.

  python tools/animator/woolcheck.py make    # writes the _WoolCheck files into src/main/resources
  python tools/animator/woolcheck.py clean   # removes them again; never commit them

Then render `Server/Models/CozyRanch/_WoolCheck_Sheep.json` with any sheep set (gen.mjs strip, MODEL=_WoolCheck_Sheep).
"""
import json, os, shutil, sys
from PIL import Image

VAN = 'C:/Users/gchou/OneDrive/Documents/Hytale-Modding/HytaleSourceCode/hytale-shared-source/HytaleAssets/Common/NPC/Livestock/Sheep/Models/'
RES = os.path.join(os.path.dirname(__file__), '..', '..', 'src', 'main', 'resources')
DIR = os.path.join(RES, 'Common', 'NPC', 'CozyRanch', '_WoolCheck')
ASSET = os.path.join(RES, 'Server', 'Models', 'CozyRanch', '_WoolCheck_Sheep.json')


def load(p):
    with open(p, encoding='utf-8-sig') as f:
        return json.load(f)


def make():
    base, wool = load(VAN + 'Model.blockymodel'), load(VAN + 'Wool_Attachment.blockymodel')
    tb, tw = Image.open(VAN + 'Texture.png'), Image.open(VAN + 'Wool_Attachment_Texture.png')
    atlas = Image.new('RGBA', (tb.width + tw.width, max(tb.height, tw.height)))
    atlas.paste(tb, (0, 0))
    atlas.paste(tw, (tb.width, 0))
    by_name, next_id = {}, [10000]

    def index(n):
        by_name.setdefault(n['name'], n)
        for c in n.get('children', []):
            index(c)
    for n in base['nodes']:
        index(n)

    def shift(n):
        n['id'] = str(next_id[0]); next_id[0] += 1
        for face in (n.get('shape', {}).get('textureLayout') or {}).values():
            face['offset']['x'] += tb.width
        for c in n.get('children', []):
            shift(c)

    def graft(n):
        piece = n.get('shape', {}).get('settings', {}).get('isPiece') and n['name'] in by_name
        for c in n.get('children', []):
            if piece and not c.get('shape', {}).get('settings', {}).get('isPiece'):
                shift(c)
                by_name[n['name']].setdefault('children', []).append(c)
            else:
                graft(c)
    for n in wool['nodes']:
        graft(n)

    os.makedirs(DIR, exist_ok=True)
    with open(os.path.join(DIR, 'Sheep_Woolly.blockymodel'), 'w', encoding='utf-8') as f:
        json.dump(base, f)
    atlas.save(os.path.join(DIR, 'Sheep_Woolly.png'))
    asset = load(os.path.join(RES, 'Server', 'Models', 'CozyRanch', 'CozyRanch_Sheep.json'))
    asset['Model'] = 'NPC/CozyRanch/_WoolCheck/Sheep_Woolly.blockymodel'
    asset['Texture'] = 'NPC/CozyRanch/_WoolCheck/Sheep_Woolly.png'
    asset.pop('RandomAttachmentSets', None)
    # vanilla clips to compare against: how far the game itself lets the wool swallow the head
    for name in ('Eat', 'Shake', 'Curious'):
        asset['AnimationSets']['Vanilla' + name] = {'Animations': [{'Animation': f'NPC/Livestock/Sheep/Animations/Flavor/{name}.blockyanim'}]}
    with open(ASSET, 'w', encoding='utf-8') as f:
        json.dump(asset, f, indent=2)
    print('wrote', DIR, ASSET)


def clean():
    shutil.rmtree(DIR, ignore_errors=True)
    if os.path.exists(ASSET):
        os.remove(ASSET)
    print('removed the _WoolCheck files')


{'make': make, 'clean': clean}[sys.argv[1] if len(sys.argv) > 1 else 'make']()
