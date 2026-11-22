#!/usr/bin/env python3
"""Exercise mounted native Maxwell Flight from runway through virtual transit and touchdown."""
import argparse
from connected_player_fixture import run_connected_player
from native_test_artifacts import retain_artifacts


@retain_artifacts
def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--xvfb', action='store_true')
    args = parser.parse_args()
    output = run_connected_player('vehicle-flight', args.xvfb, ['vandorlabs.vehicleFlightChecks=true'])
    print('Native Maxwell player flight passed: ' + str(output), flush=True)


if __name__ == '__main__':
    main()
