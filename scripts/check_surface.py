"""Validate the retained entry points, single-page view graph and resources."""
from pathlib import Path
import re
import xml.etree.ElementTree as ET

ROOT = Path(__file__).resolve().parents[1]
MAIN = ROOT / 'app/src/main'
RES = MAIN / 'res'
A = '{http://schemas.android.com/apk/res/android}'

manifest = ET.parse(MAIN / 'AndroidManifest.xml').getroot()
application = manifest.find('application')
activities = {n.get(A + 'name') for n in application.findall('activity')}
assert activities == {'.MainActivity', '.ShortcutActionActivity', '.TileActionActivity'}, activities
receivers = application.findall('receiver')
assert [n.get(A + 'name') for n in receivers] == ['.BootReceiver']
assert receivers[0].get(A + 'exported') == 'false'
assert not application.findall('receiver/intent-filter/action[@' + A + 'name="com.dhangofa.networktoggle.SET_MODE"]')

def expand_layout(name, stack=(), override=None):
    assert name not in stack, ('recursive include', name)
    node = ET.parse(RES / 'layout' / (name + '.xml')).getroot()
    if override: node.set(A + 'id', override)
    yield node
    for child in node.iter():
        if child is not node and child.tag != 'include':
            yield child
        if child.tag == 'include':
            yield from expand_layout(child.get('layout').split('/')[1], stack + (name,), child.get(A + 'id'))

page = list(expand_layout('activity_main'))
ids = [n.get(A + 'id', '').split('/')[-1] for n in page if n.get(A + 'id')]
assert len(ids) == len(set(ids)), ('duplicate view IDs', ids)
assert sum(n.tag == 'ScrollView' for n in page) == 1
assert ids.index('shortcutsContainer') > ids.index('cardTileCycle')
assert {'btnSetupAuthorize', 'btnAddShortcut', 'shizukuStatusText'} <= set(ids)
assert not (RES / 'layout-land/activity_main.xml').exists()

resources = set()
all_xml = list(RES.rglob('*.xml'))
for path in all_xml:
    kind = path.parent.name.split('-')[0]
    if kind == 'values':
        for node in ET.parse(path).getroot():
            if node.get('name'):
                resources.add((node.get('type') if node.tag == 'item' else node.tag, node.get('name')))
    else:
        resources.add((kind, path.stem))
    for node in ET.parse(path).getroot().iter():
        value = node.get(A + 'id', '')
        if value.startswith('@+id/'):
            resources.add(('id', value.split('/')[1]))

java = list((MAIN / 'java').rglob('*.java'))
for path in java:
    text = path.read_text(encoding='utf-8')
    assert not re.search(r'ExecutionMode\.ROOT|\b(?:su|app_process)\b', text), path
    assert 'com.dhangofa.networktoggle.automation' not in text, path
    for kind, name in re.findall(r'\bR\.(\w+)\.([\w]+)', text):
        if ('android.R.' + kind + '.' + name) in text:
            continue
        if kind == 'style': name = name.replace('_', '.')
        assert (kind, name) in resources, (path.name, kind, name)
for path in all_xml + [MAIN / 'AndroidManifest.xml']:
    text = path.read_text(encoding='utf-8')
    for kind, name in re.findall(r'(?<!android:)@\+?(\w+)/([\w.]+)', text):
        assert (kind, name) in resources, (path.name, kind, name)

print('Surface checks passed: Shizuku-only entry points, one page, shortcuts at bottom, all references resolve.')
