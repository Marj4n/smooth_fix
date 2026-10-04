"""Create a verification-only village replacement datapack in a disposable world.
Usage: python verification/create_village_fixture.py WORLD EXTRACTED_MINECRAFT_SERVER_JAR
Do not use this pack in a normal modpack save.
"""
from pathlib import Path
import json
import sys
import zipfile

world, game_jar = map(Path, sys.argv[1:3])
pack = world / 'datapacks/smoothfix_village_fixture'
pack.mkdir(parents=True, exist_ok=True)
(pack / 'pack.mcmeta').write_text(json.dumps({'pack': {'pack_format': 15, 'description': 'Smooth Fix verification village replacement'}}))
with zipfile.ZipFile(game_jar) as game:
    for name, source in [('replacement', 'village_plains'), ('outpost', 'pillager_outpost')]:
        target = pack / f'data/smoothfix_fixture/worldgen/structure/{name}.json'
        target.parent.mkdir(parents=True, exist_ok=True)
        target.write_bytes(game.read(f'data/minecraft/worldgen/structure/{source}.json'))
    villages = json.loads(game.read('data/minecraft/worldgen/structure_set/villages.json'))
    villages['structures'] = [{'structure': 'smoothfix_fixture:replacement', 'weight': 1}]
    target = pack / 'data/minecraft/worldgen/structure_set/villages.json'
    target.parent.mkdir(parents=True, exist_ok=True)
    target.write_text(json.dumps(villages))
print(pack)
