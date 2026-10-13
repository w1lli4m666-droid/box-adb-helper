"""Check translation coverage, formatting and localized launcher labels."""
from pathlib import Path
import re
import xml.etree.ElementTree as ET

root = Path(__file__).resolve().parents[1]
def strings(path):
    return {x.attrib['name']: x.text or '' for x in ET.parse(path).getroot()}

default = strings(root/'res/values/strings.xml')
assert default['app_name'] == 'Box ADB Helper'
for path in sorted((root/'res').glob('values*/strings.xml')):
    translated = strings(path)
    assert translated.keys() == default.keys(), f'Missing translations: {path}'
    for key, text in translated.items():
        assert sorted(re.findall(r'%\d+\$[ds]', text)) == sorted(re.findall(r'%\d+\$[ds]', default[key])), (path, key)
    print(f'PASS {path.parent.name}: {len(translated)} strings')
manifest = ET.parse(root/'AndroidManifest.xml').getroot()
assert manifest.find('application').attrib['{http://schemas.android.com/apk/res/android}label'] == '@string/app_name'
assert not re.search(r'[\u4e00-\u9fff]', (root/'src/cn/boxadb/helper/MainActivity.java').read_text(encoding='utf-8'))
print('PASS resource coverage, format arguments, launcher label, and visible string extraction')
