#!/usr/bin/env python3
"""Exercise mounted native Maxwell Flight from runway through virtual transit and touchdown."""
import argparse
from connected_player_fixture import run_connected_player
from native_test_artifacts import retain_artifacts


@retain_artifacts
def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--xvfb', action='store_true')
    modes = parser.add_mutually_exclusive_group()
    modes.add_argument('--restart', action='store_true')
    modes.add_argument('--impact', action='store_true')
    args = parser.parse_args()
    properties = ['vandorlabs.vehicleFlightChecks=true']
    if args.restart:
        saved = run_connected_player('vehicle-flight-write', args.xvfb, properties + ['vandorlabs.vehicleFlightRestart=write'])
        output = run_connected_player('vehicle-flight-read', args.xvfb, properties + ['vandorlabs.vehicleFlightRestart=read'],
                                      saved_world=saved / 'server' / 'world')
        print('Native Maxwell high-flight and mounted cold restart passed: ' + str(output), flush=True)
    elif args.impact:
        for variant in ('hover', 'runway'):
            output = run_connected_player('vehicle-flight-impact-' + variant, args.xvfb,
                                          properties + ['vandorlabs.vehicleFlightImpact=' + variant])
            print('Native Flight hull impact (' + variant + ') passed: ' + str(output), flush=True)
    else:
        output = run_connected_player('vehicle-flight', args.xvfb, properties)
        print('Native Maxwell player flight passed: ' + str(output), flush=True)


if __name__ == '__main__':
    main()
