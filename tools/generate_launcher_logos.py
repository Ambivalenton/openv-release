from pathlib import Path
import xml.etree.ElementTree as E,json,re
r=Path(__file__).resolve().parents[1];res=r/'app/src/main/res';base=r/'app/src/main/java/dev/ambivalenton/openv'
# Native vector artwork: same brand contours for monograms, original transit symbols for other styles.
ns={'a':'http://schemas.android.com/apk/res/android'}
original=E.parse(res/'drawable/ic_launcher_foreground.xml').getroot()
mark_paths=[p.get('{'+ns['a']+'}pathData')for p in original.findall('.//path')]
def path(d,c,alpha=1,stroke=None,width=1):
 attrs=f'android:fillType="evenOdd" android:pathData="{d}" android:fillColor="{c}"'
 if alpha!=1:attrs+=f' android:fillAlpha="{alpha}"'
 if stroke:attrs+=f' android:strokeColor="{stroke}" android:strokeWidth="{width}" android:strokeLineCap="round" android:strokeLineJoin="round"'
 return '<path '+attrs+'/>'
def rect(x,y,w,h,c):return path(f'M{x},{y}h{w}v{h}h-{w}Z',c)
def circle(x,y,rad,c,alpha=1):return path(f'M{x-rad},{y}a{rad},{rad} 0,1 0,{rad*2},0 a{rad},{rad} 0,1 0,-{rad*2},0Z',c,alpha)
def mark(a,b):return '<group android:scaleX="0.039" android:scaleY="0.039" android:translateX="22.5" android:translateY="23">'+path(mark_paths[0],a)+path(mark_paths[1],b)+'</group>'
def tram(body,window,accent):
 return path('M40,30Q30,30 30,40V68Q30,76 38,76H70Q78,76 78,68V40Q78,30 68,30Z',body)+path('M37,39H71V56H37Z',window)+rect(51,39,3,17,body)+circle(40,65,3,accent)+circle(68,65,3,accent)+path('M40,76L35,82M68,76L73,82','#00000000',stroke=accent,width=3)+path('M45,24H63M54,24V30','#00000000',stroke=accent,width=3)
items=[
('MODERN','Metro modern','Klare ÖV-Konturen auf kräftigem Petrol','Modern metro','Clean ÖV contours on deep teal','#006F79',mark('#FFFFFF','#B9F1DF')),
('MINIMAL','Minimal','Schwarzweiß und ein klares Zeichen','Minimal','Black and white with a clean mark','#F5F5F0',mark('#172327','#172327')),
('PIXEL','Pixelbahn','Eine kleine Bahn aus klaren Pixeln','Pixel train','A small train made of crisp pixels','#192A40',rect(39,30,30,5,'#FFD96A')+rect(34,35,40,35,'#8CE5D8')+rect(39,70,30,5,'#8CE5D8')+rect(39,40,12,15,'#192A40')+rect(56,40,12,15,'#192A40')+rect(39,60,6,5,'#FFD96A')+rect(63,60,6,5,'#FFD96A')+rect(39,75,5,7,'#D8F7F1')+rect(64,75,5,7,'#D8F7F1')+rect(49,25,10,5,'#FFD96A')),
('RETRO','Retro-Emaille','Warme Emaillefarben wie an alten Haltestellen','Retro enamel','Warm enamel colours from old transit signs','#EFE3C6',circle(54,54,32,'#315A51')+circle(54,54,28,'#F6EACB')+tram('#315A51','#F6EACB','#B44F3C')),
('DECO','Art déco','Goldene Geometrie auf tiefem Nachtblau','Art deco','Golden geometry on deep midnight blue','#192C3C',path('M54,23L82,44V66L54,85L26,66V44Z','#DABC72')+path('M54,29L76,46V63L54,79L32,63V46Z','#192C3C')+path('M40,39H68V62H40Z','#DABC72')+rect(44,43,20,10,'#192C3C')+path('M45,64L39,73M63,64L69,73M54,24V34','#00000000',stroke='#DABC72',width=2)+circle(45,58,2,'#192C3C')+circle(63,58,2,'#192C3C')),
('GLASS','Glaslicht','Lichtkanten und überlagerte Glasflächen','Glass light','Bright edges and layered glass surfaces','#C8E3F3',circle(42,40,26,'#EEF9FF',.8)+circle(73,74,26,'#90BCD9',.65)+path('M28,30Q28,24 35,24H75Q83,24 83,32V76Q83,83 76,83H34Q27,83 27,76Z','#FFFFFF',.35)+mark('#296D9B','#235277')),
('MAP','Linienplan','Bunte Linien und Haltepunkte als offenes V','Route map','Colourful lines and stops forming an open V','#F4F0E5',path('M30,30L54,78L78,30M30,58H77','#00000000',stroke='#167A83',width=7)+circle(30,30,6,'#E89579')+circle(54,78,6,'#E89579')+circle(78,30,6,'#E89579')+circle(44,58,5,'#F4F0E5')+circle(64,58,5,'#F4F0E5')),
('ORIGAMI','Origami','Gefaltetes Papier, das zu deinem Weg wird','Origami','Folded paper becoming your route','#D9F0E9',path('M25,32L49,43L56,77Z','#2D7780')+path('M25,32L56,77L48,52Z','#50A799')+path('M49,43L83,28L56,77Z','#153F5D')+path('M49,43L83,28L64,52Z','#78BFC0')),
('SUNRISE','Morgenrot','Ein heller Aufbruch zwischen Sonne und Schienen','Sunrise','A fresh start between sunshine and rails','#D26557',circle(54,40,20,'#FFD998')+path('M25,53Q54,43 83,53V85H25Z','#B5504E')+tram('#FFF1D4','#A64B49','#FDD79E')),
('ORBIT','Orbit','Eine kleine Bahn im großen Sternenraum','Orbit','A small train in a wide starry sky','#292843',path('M24,66C14,43 80,19 85,41C91,60 31,86 24,66Z','#00000000',stroke='#C6BCE7',width=2)+circle(54,54,22,'#7C7BA6')+path('M40,41L54,69L68,41','#00000000',stroke='#F6E7B5',width=6)+circle(78,27,3,'#F6E7B5')+circle(28,36,2,'#F6E7B5')),
('PLAY','Spielwürfel','Fröhliche Formen und ein kleines Fahrziel','Play block','Cheerful shapes and a small destination','#F3DAD2',path('M54,24L81,39V70L54,85L27,70V39Z','#596DB0')+path('M54,24L81,39L54,55L27,39Z','#E6AA52')+path('M27,39L54,55V85L27,70Z','#D16D70')+circle(65,62,5,'#FCF0D8')+circle(40,53,4,'#FCF0D8')+circle(40,68,4,'#FCF0D8')+circle(54,39,4,'#FCF0D8')),
('NIGHT','Nachtlinie','Leuchtende Bahn vor einem ruhigen Nachthimmel','Night line','A glowing train under a calm night sky','#102D39',circle(74,30,11,'#E8DCA1')+circle(79,26,10,'#102D39')+tram('#9FD5CF','#183C48','#F2DDA6')+circle(30,26,2,'#F2DDA6')),
]
# Colour backgrounds and monochrome masks are separate adaptive layers; previews use the same paths.
for key,de,desc,en,edesc,bg,fg in items:
 slug=key.lower();header='<?xml version="1.0" encoding="utf-8"?>\n<!-- SPDX-License-Identifier: GPL-3.0-or-later; original Öpen-V artwork. -->\n'
 v='<vector xmlns:android="http://schemas.android.com/apk/res/android" android:width="108dp" android:height="108dp" android:viewportWidth="108" android:viewportHeight="108">'
 (res/f'drawable/logo_{slug}_foreground.xml').write_text(header+v+fg+'</vector>\n')
 (res/f'drawable/logo_{slug}_preview.xml').write_text(header+v+rect(0,0,108,108,bg)+fg+'</vector>\n')
 # The monochrome layer uses the same silhouette and preserves intentional cutouts in pixel/window shapes.
 mono=re.sub(r'android:fillColor="(?!#00000000)[^"]+"','android:fillColor="#FFFFFF"',fg)
 mono=re.sub(r'android:strokeColor="[^"]+"','android:strokeColor="#FFFFFF"',mono)
 if key in ('RETRO','SUNRISE','NIGHT'):
  mono=path('M40,30Q30,30 30,40V68Q30,76 38,76H70Q78,76 78,68V40Q78,30 68,30Z M37,39H71V56H37Z M38,62H44V68H38Z M64,62H70V68H64Z','#FFFFFF')+path('M40,76L35,82M68,76L73,82M45,24H63M54,24V30','#00000000',stroke='#FFFFFF',width=3)
 if key=='PIXEL':mono=path('M39,30H69V35H74V70H69V75H39V70H34V35H39Z M39,40H51V55H39Z M56,40H68V55H56Z M39,60H45V65H39Z M63,60H69V65H63Z','#FFFFFF')+rect(39,75,5,7,'#FFFFFF')+rect(64,75,5,7,'#FFFFFF')+rect(49,25,10,5,'#FFFFFF')
 if key=='ORBIT':mono=path('M32,54a22,22 0,1 0,44,0 a22,22 0,1 0,-44,0Z M36,54a18,18 0,1 0,36,0 a18,18 0,1 0,-36,0Z','#FFFFFF')+path('M43,44L54,66L65,44','#00000000',stroke='#FFFFFF',width=4)+circle(79,28,3,'#FFFFFF')
 if key=='PLAY':mono=path('M54,24L81,39V70L54,85L27,70V39Z M54,30L33,42V67L54,79L75,67V42Z','#FFFFFF')+path('M31,41L54,55L78,41M54,55V81','#00000000',stroke='#FFFFFF',width=4)
 if key=='DECO':mono=path('M54,23L82,44V66L54,85L26,66V44Z M54,29L76,46V63L54,79L32,63V46Z M40,39H68V62H40Z M44,43H64V53H44Z','#FFFFFF')+path('M45,64L39,73M63,64L69,73M54,24V34','#00000000',stroke='#FFFFFF',width=2)
 (res/f'drawable/logo_{slug}_monochrome.xml').write_text(header+v+mono+'</vector>\n')
 (res/f'drawable/logo_{slug}_background.xml').write_text(header+v+rect(0,0,108,108,bg)+'</vector>\n')
 (res/f'mipmap-anydpi-v26/logo_{slug}.xml').write_text(header+f'<adaptive-icon xmlns:android="http://schemas.android.com/apk/res/android"><background android:drawable="@drawable/logo_{slug}_background"/><foreground android:drawable="@drawable/logo_{slug}_foreground"/><monochrome android:drawable="@drawable/logo_{slug}_monochrome"/></adaptive-icon>\n')
# Default preview keeps the exact supplied identity.
v='<vector xmlns:android="http://schemas.android.com/apk/res/android" android:width="108dp" android:height="108dp" android:viewportWidth="108" android:viewportHeight="108">'
(res/'drawable/logo_original_preview.xml').write_text(v+rect(0,0,108,108,'#FFFFFF')+mark('#006A6A','#001C3B')+'</vector>\n')
entries=[('ORIGINAL','Original','Das vertraute Öpen-V-Zeichen','Original','The familiar Öpen-V mark')]+[x[:5]for x in items]
kt='''// SPDX-License-Identifier: GPL-3.0-or-later
package dev.ambivalenton.openv.data.model
import dev.ambivalenton.openv.R
import dev.ambivalenton.openv.localization.tr

/** Component names are persisted by Android. Never rename or remove an existing alias. */
enum class AppLogo(val titleKey:String,val descriptionKey:String,val preview:Int,val launcher:Int) {
'''+',\n'.join(f'    {key}({json.dumps(de,ensure_ascii=False)},{json.dumps(desc,ensure_ascii=False)},R.drawable.logo_{key.lower()}_preview,R.mipmap.{"ic_launcher" if key=="ORIGINAL" else "logo_"+key.lower()})'for key,de,desc,en,edesc in entries)+''';
    val title get()=tr(titleKey)
    val description get()=tr(descriptionKey)
    val alias get()="dev.ambivalenton.openv.launcher.Logo"+name
}
'''
(base/'data/model/AppLogo.kt').write_text(kt)
p=r/'localization/en.json';catalog=json.loads(p.read_text())
for key,de,desc,en,edesc in entries:catalog[de]=en;catalog[desc]=edesc
p.write_text(json.dumps(catalog,indent=2,ensure_ascii=False,sort_keys=True)+'\n')
p=r/'app/src/main/AndroidManifest.xml';s=p.read_text();s=s.replace('   <intent-filter><action android:name="android.intent.action.MAIN"/><category android:name="android.intent.category.LAUNCHER"/></intent-filter>\n','',1)
s=re.sub(r'  <activity-alias android:name=".launcher.Logo.*?</activity-alias>\n','',s,flags=re.S)
aliases=''
for key,*_ in entries:
 icon='ic_launcher'if key=='ORIGINAL'else'logo_'+key.lower()
 aliases+=f'  <activity-alias android:name=".launcher.Logo{key}" android:targetActivity=".MainActivity" android:enabled="{str(key=="ORIGINAL").lower()}" android:exported="true" android:icon="@mipmap/{icon}" android:label="@string/app_name">\n   <intent-filter><action android:name="android.intent.action.MAIN"/><category android:name="android.intent.category.LAUNCHER"/></intent-filter>\n  </activity-alias>\n'
s=s.replace('  <service android:name=".tracking.PinnedJourneyService"',aliases+'  <service android:name=".tracking.PinnedJourneyService"');p.write_text(s)
(r/'docs/app-logos.json').write_text(json.dumps({'artwork':'Original Android vectors; no external image or proprietary font assets','logos':[{'id':key,'name':de,'description':desc}for key,de,desc,en,edesc in entries],'sources':['https://developer.android.com/guide/topics/manifest/activity-alias-element','https://developer.android.com/reference/android/content/pm/PackageManager','https://developer.android.com/develop/ui/compose/system/icon_design_adaptive']},ensure_ascii=False,indent=2)+'\n')
print('13 launcher identities, 12 new vector logos generated')
