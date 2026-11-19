#!/usr/bin/env python3
"""Generate the native material slice from canonical legacy models, recipes and artwork."""
import json
import shutil
import sys
from pathlib import Path

ROOT = Path(__file__).resolve().parents[2]
OUT = Path(__file__).resolve().parent / 'build/generated/matter-resources'
sys.path.insert(0, str(ROOT / 'tools'))
from compact_runtime_assets import resized


def write(relative, value):
    path = OUT / relative
    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_text(json.dumps(value, indent=2) + '\n')


CANONICAL = ROOT / 'src/main/resources/assets/vandorlabs'


def model(relative):
    value = json.loads((CANONICAL / relative).read_text())
    if value.get('parent', '').startswith(('block/', 'item/')):
        value['parent'] = 'minecraft:' + value['parent']
    value['textures'] = {key: texture.replace(':blocks/', ':block/').replace(':items/', ':item/')
                         for key, texture in value.get('textures', {}).items()}
    write('assets/vandorlabs/' + relative, value)


def main():
    if OUT.exists():
        shutil.rmtree(OUT)
    ids = ['programmable_matter_ingot', 'industrial_alloy_ingot']
    for name in ids + ['programmable_plate']:
        model('models/item/' + name + '.json')
    sources = {
        'textures/block/matter/matter.png': 'texture-packs/additional/assets/vandorlabs/textures/blocks/matter/matter.png',
        **{'textures/item/' + name + '.png': 'texture-packs/default/assets/vandorlabs/textures/items/' + name + '.png'
           for name in ids},
    }
    for target, source in sources.items():
        output = OUT / 'assets/vandorlabs' / target
        output.parent.mkdir(parents=True, exist_ok=True)
        shutil.copy2(ROOT / source, output)
        resized(output, 128)
    for gui in ('vandor_logo.png', 'source_buttons.png'):
        target = OUT / 'assets/vandorlabs/textures/gui' / gui
        target.parent.mkdir(parents=True, exist_ok=True)
        shutil.copy2(ROOT / 'texture-packs/additional/assets/vandorlabs/textures/gui' / gui, target)
    lang = {}
    wanted = ['itemGroup.vandorlabs', 'tile.vandorlabs.programmable_block.name', 'tile.vandorlabs.programmable_slab.name', 'tile.vandorlabs.programmable_stairs.name', 'tile.vandorlabs.programmable_wall.name', 'tile.vandorlabs.programmable_porthole_wall.name', 'tile.vandorlabs.programmable_porthole_block.name', 'tile.vandorlabs.programmable_diagonal_wall.name', 'tile.vandorlabs.programmable_diagonal_porthole.name', 'tile.vandorlabs.programmable_diagonal_glass.name', 'tile.vandorlabs.programmable_trigger_block.name', 'tile.vandorlabs.push_button.name', 'tile.vandorlabs.rocker_switch.name', 'tile.vandorlabs.compact_power_lever.name', 'tile.vandorlabs.industrial_power_lever.name', 'tile.vandorlabs.small_power_lever.name', 'tile.vandorlabs.large_power_lever.name', 'tile.vandorlabs.thruster_lever.name', 'tile.vandorlabs.airliner_throttle.name', 'tile.vandorlabs.fighter_throttle.name', 'tile.vandorlabs.wall_slider.name', 'tile.vandorlabs.redstone_transmitter_block.name', 'tile.vandorlabs.redstone_receiver_block.name', 'item.vandorlabs.configurizer.name'] + ['tile.vandorlabs.' + name + '.name' for name in ('programmable_door', 'space_rotating_door', 'space_sliding_door', 'large_programmable_door', 'segmented_door', 'programmable_trapdoor', 'programmable_diagonal_trapdoor', 'programmable_light', 'programmable_light_frame', 'programmable_light_slab', 'programmable_viewscreen', 'programmable_console', 'programmable_half_console', 'programmable_half_input', 'programmable_input', 'programmable_diagonal_screen', 'programmable_diagonal_half_screen')] + [
        'item.vandorlabs.' + name + '.name' for name in ids + ['programmable_plate']]
    wanted += ['tile.vandorlabs.' + name + '.name' for name in ('landing_gear','ski_landing_gear','landing_strut','angled_landing_strut')]
    wanted += ['tile.vandorlabs.programmable_chair.name','tile.vandorlabs.luxury_seat.name','tile.vandorlabs.military_seat.name','tile.vandorlabs.pilot_seat.name','tile.vandorlabs.chair_siderests.name','tile.vandorlabs.yoke_screen.name','tile.vandorlabs.cockpit.name']
    wanted += ['tile.vandorlabs.wheel_small.name','tile.vandorlabs.wheel_medium.name','tile.vandorlabs.wheel_large.name','tile.vandorlabs.compact_track.name','tile.vandorlabs.armoured_track.name','tile.vandorlabs.bogie_track.name']
    wanted += ['tile.vandorlabs.turntable_surface.name','tile.vandorlabs.turntable_controller.name','tile.vandorlabs.copy_controller.name']
    wanted += ['tile.vandorlabs.industrial_table.name','tile.vandorlabs.programmable_pressure_plate.name']
    wanted += ['tile.vandorlabs.' + name + '.name' for name in ('rocket_thruster', 'ion_drive', 'plasma_vent', 'impulse_engine', 'antigravity_plate', 'repulsor_array', 'vertical_hover_thruster')]
    wanted += ['tile.vandorlabs.' + name + '.name' for name in ('access_card_slot','data_chip_slot')]
    wanted += ['tile.vandorlabs.programmable_glass.name','tile.vandorlabs.programmable_storage.name','tile.vandorlabs.data_chip_programmer.name']
    wanted += ['item.vandorlabs.duplifier.name']
    wanted += ['tile.vandorlabs.module_rack.name','item.vandorlabs.blank_module.name','item.vandorlabs.autopilot_module.name','item.vandorlabs.battery_module.name','message.vandorlabs.module_rack.inventory_full']
    wanted += ['tile.vandorlabs.' + name + '.name' for name in ('power_port', 'fluid_port', 'matter_port', 'battery_block', 'hexagonal_fuel_tank')]
    wanted += ['tile.vandorlabs.' + name + '.name' for name in ('gear_extender', 'gear_swing_arm', 'large_gear_extender', 'large_gear_swing_arm', 'gear_mount', 'large_gear_mount', 'landing_plate', 'landing_ski', 'landing_wheel', 'large_landing_wheel')]
    wanted += ['tile.vandorlabs.programmable_ramp.name', 'tile.vandorlabs.programmable_ramp_slab.name']
    wanted += ['tile.vandorlabs.' + name + '_core.name' for name in ('synthesis_tower', 'assembly_press', 'enclosed_gantry', 'standalone_antenna', 'standalone_dish', 'longburn_drive', 'meridian_drive', 'oculus_drive', 'vector_fold_array', 'paladyne_navigation', 'seyr_navigation')]
    wanted += ['tile.vandorlabs.programmable_matter_infuser.name', 'item.vandorlabs.infuser_core.name']
    for line in (ROOT / 'generated-resources/assets/vandorlabs/lang/en_us.lang').read_text().splitlines():
        key, separator, value = line.partition('=')
        if separator and key in wanted:
            lang[key.replace('tile.', 'block.', 1).removesuffix('.name')] = value
    if len(lang) != len(wanted):
        raise ValueError('Missing canonical material translations')
    from generate_credential_resources import translations as credential_translations
    lang.update(credential_translations(ROOT))
    from generate_canopy_resources import translations as canopy_translations
    lang.update(canopy_translations(ROOT))
    lang.update({'block.vandorlabs.navigation_table_flat':'Navigation Table','block.vandorlabs.navigation_table_angled':'Angled Navigation Table','item.vandorlabs.navigation_data_module':'Navigation Data Module'})
    from generate_appliance_resources import translations as appliance_translations
    lang.update(appliance_translations(ROOT))
    lang.update({f'item.vandorlabs.programmable_{piece.lower()}': f'Programmable {piece}'
                 for piece in ('Helmet', 'Chestplate', 'Leggings', 'Boots')})
    lang['item.vandorlabs.programmable_armor.hint'] = (
        'Shift-right-click a block to sample its face. In air: texture picker (creative or Configurizer).')
    lang['item.vandorlabs.programmable_armor.sampled'] = 'Sampled %s'
    lang['item.vandorlabs.programmable_armor.no_sample'] = 'No supported face texture to sample.'
    lang['item.vandorlabs.programmable_armor.copied'] = 'Duplifier copied %s'
    lang['item.vandorlabs.programmable_armor.applied'] = 'Duplifier applied armor texture'
    lang['item.vandorlabs.programmable_armor.no_copy'] = 'No copied armor texture applies here'
    lang['block.vandorlabs.cockpit.displays']='Cockpit Displays'
    lang['block.vandorlabs.cockpit.screen_lift_blocked']='The Cockpit screen bank is obstructed.'
    write('assets/vandorlabs/lang/en_us.json', lang)
    for name in ['industrial_alloy_ingot']:
        value = json.loads((CANONICAL / 'recipes' / (name + '.json')).read_text())
        value['result']['id'] = value['result'].pop('item')
        write('data/vandorlabs/recipe/' + name + '.json', value)
    propulsion = [family + suffix for family in ('rocket_thruster', 'ion_drive', 'plasma_vent', 'impulse_engine') for suffix in ('', '_hexagonal', '_wedge', '_wedge_bottom_right', '_wedge_top_right', '_wedge_top_left')] + ['antigravity_plate', 'repulsor_array', 'vertical_hover_thruster']
    for tag in ['mineable/pickaxe', 'needs_stone_tool']:
        optional_trapdoors = [{'id': 'vandorlabs:' + name, 'required': False} for name in ('programmable_trapdoor', 'programmable_diagonal_trapdoor')] if tag == 'mineable/pickaxe' else []
        optional_trapdoors += [{'id':'vandorlabs:'+name,'required':False} for name in ('turntable_surface','turntable_controller','copy_controller')]
        optional_trapdoors += [{'id':'vandorlabs:industrial_table','required':False}]
        optional_trapdoors += [{'id': 'vandorlabs:' + name, 'required': False} for name in ('gear_extender', 'gear_swing_arm', 'large_gear_extender', 'large_gear_swing_arm', 'gear_mount', 'large_gear_mount', 'landing_plate', 'landing_ski', 'landing_wheel', 'large_landing_wheel')] if tag == 'mineable/pickaxe' else []
        optional_trapdoors += [{'id':'vandorlabs:module_rack','required':False}]
        optional_trapdoors += [{'id':'vandorlabs:'+name,'required':False} for name in ('navigation_table_flat','navigation_table_angled','standalone_dish_core','standalone_antenna_core','observer_part','printer','scanner')]
        optional_trapdoors += [{'id':'vandorlabs:programmable_matter_infuser','required':False}]
        optional_trapdoors += [{'id':'vandorlabs:'+name+'_core','required':False} for name in ('synthesis_tower','assembly_press','enclosed_gantry','longburn_drive','meridian_drive','oculus_drive','vector_fold_array','paladyne_navigation','seyr_navigation')]
        optional_trapdoors += [{'id':'vandorlabs:'+name,'required':False} for name in ('landing_gear','ski_landing_gear','landing_strut','angled_landing_strut')]
        optional_trapdoors += [{'id':'vandorlabs:'+name,'required':False} for name in ('programmable_chair','luxury_seat','military_seat','pilot_seat','chair_siderests','yoke_screen','cockpit')]
        optional_trapdoors += [{'id':'vandorlabs:'+name,'required':False} for name in ('compact_track','armoured_track','bogie_track','wheel_small','wheel_medium','wheel_large')]
        optional_trapdoors += [{'id': 'vandorlabs:' + name, 'required': False} for name in ('power_port', 'fluid_port', 'matter_port', 'battery_block', 'hexagonal_fuel_tank')] if tag == 'mineable/pickaxe' else []
        optional_trapdoors += [{'id': 'vandorlabs:' + name, 'required': False} for name in ('power_port', 'fluid_port', 'matter_port', 'battery_block', 'hexagonal_fuel_tank')] if tag == 'needs_stone_tool' else []
        write('data/minecraft/tags/block/' + tag + '.json', {'replace': False, 'values': ['vandorlabs:programmable_block','vandorlabs:programmable_slab','vandorlabs:programmable_stairs','vandorlabs:programmable_wall','vandorlabs:programmable_porthole_wall','vandorlabs:programmable_porthole_block','vandorlabs:programmable_diagonal_wall','vandorlabs:programmable_diagonal_porthole','vandorlabs:programmable_diagonal_glass','vandorlabs:programmable_trigger_block','vandorlabs:push_button','vandorlabs:rocker_switch','vandorlabs:compact_power_lever','vandorlabs:industrial_power_lever','vandorlabs:small_power_lever','vandorlabs:large_power_lever','vandorlabs:thruster_lever','vandorlabs:airliner_throttle','vandorlabs:fighter_throttle','vandorlabs:wall_slider','vandorlabs:redstone_transmitter_block','vandorlabs:redstone_receiver_block'] + [{'id': 'vandorlabs:' + name, 'required': False} for name in ('programmable_door', 'space_rotating_door', 'space_sliding_door', 'large_programmable_door', 'segmented_door')] + [{'id':'vandorlabs:'+name,'required':False} for name in ('programmable_viewscreen','programmable_console','programmable_half_console','programmable_half_input','programmable_input','programmable_diagonal_screen','programmable_diagonal_half_screen')] + [{'id':'vandorlabs:'+name,'required':False} for name in ('access_card_slot','data_chip_slot')] + [{'id':'vandorlabs:programmable_storage','required':False}] + ([{'id':'vandorlabs:data_chip_programmer','required':False}] if tag == 'mineable/pickaxe' else []) + optional_trapdoors + [{'id': 'vandorlabs:' + name, 'required': False} for name in ('programmable_ramp', 'programmable_ramp_slab')] + [{'id': 'vandorlabs:' + name, 'required': False} for name in propulsion] + [{'id': 'vandorlabs:' + name, 'required': False} for name in ('programmable_light', 'programmable_light_frame', 'programmable_light_slab')]})
    write('pack.mcmeta', {'pack': {'pack_format': 34, 'description': 'Vandor Labs native material slice'}})


if __name__ == '__main__':
    main()
