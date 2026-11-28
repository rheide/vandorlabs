# Vandor Labs 3.1

Vandor Labs 3.1 rebalances Forge 1.12.2 Flight so that a craft's weight, its drives and its thrusters all matter. A lean, well-matched craft takes off sooner, flies farther and hovers faster than one built by stacking every part. NeoForge 1.21.1 keeps the 3.0 drive, runway and speed rules.

Plan a build with the [flight calculator](https://htmlpreview.github.io/?https://github.com/rheide/vandorlabs/blob/master/docs/flight-calculator.html), or open `docs/flight-calculator.html` from a checkout to use it offline. It shows every number below for your own craft, including a takeoff and climb profile, range by throttle and how long a trip takes. **Copy link** saves the build in a short link you can share.

## Display Arm

The Forge [Programmable Display Arm](../display-arm.md) combines full/half-height screens and an optional input deck in one block. Mount its plate on any block face, then set the screen's position and angles independently in the Arm tab.

## Maxwell Drive thrust

Each Maxwell thrust unit now lifts **1,024 effective weight units** at takeoff, twice the 3.0 figure, on Forge and NeoForge. A single 3 × 3 × 4 drive takes a 1,000-unit craft off the ground, and every drive supports twice the weight at each altitude. The [Maxwell Drive guide](../multiblocks/maxwell-drive/README.md#how-many-drives-do-i-need) lists the new limits. Drive weight, tank size and burn per thrust unit are unchanged, so a craft that already flew now runs further below its takeoff limit: it needs a shorter runway, burns less fuel and climbs higher.

## Runways

A craft lifts off at **0.6 blocks per tick (43.2 kb/h)**. The run it needs depends on how close its effective weight is to the takeoff limit on the Vehicle Controller's Flight tab:

| Effective weight ÷ takeoff limit | Runway |
| --- | ---: |
| Light | 30 blocks |
| 25% | 41 blocks |
| 50% | 73 blocks |
| 75% | 126 blocks |
| At the limit | 200 blocks |

Hold **W** along the runway and press **S** once the craft reaches takeoff speed. Selecting Flight from Hover still needs no runway.

## Speed below the cruise band

Below Y=256, powered Flight targets **72 kb/h** at the takeoff limit and rises to **144 kb/h** once the drives give twice the thrust the craft needs. Above Y=256, airspeed still builds with altitude toward 1,080 kb/h at Y=10,000.


On a landing approach the aircraft slows itself. Within 30 blocks of the ground, averaged over the last few ticks, the speed target eases down to the 43.2 kb/h takeoff speed near touchdown. Pulling up into a climb, or flying higher than 30 blocks above the ground, returns to the normal speed.
## Maxwell Drive fuel

Each running drive burns **100 mB per minute per thrust unit** at full throttle when the craft is at its takeoff limit. A lighter craft burns less: 85% of the burn follows effective weight ÷ takeoff limit, so an almost empty craft burns 15% of the full rate. Analog throttle still scales thrust and burn together. A placed drive fired by redstone burns at the full rate.

Drive sizes now trade weight against fuel. Larger drives weigh less for their thrust but burn more fuel per thrust unit. Each drive's own tank is a small buffer:

| Drive | Thrust units | Dry weight (3.0) | Tank | Burn at full throttle and takeoff limit | Own tank lasts |
| --- | ---: | ---: | ---: | ---: | ---: |
| 3 × 3 × 4 | 1 | 160 (160) | 250 mB | 100 mB/min | 2.5 min |
| 4 × 4 × 5 | 2.22 | 333 (356) | 556 mB | 240 mB/min | 2.3 min |
| 5 × 5 × 7 | 4.86 | 681 (778) | 1,215 mB | 564 mB/min | 2.2 min |
| 6 × 6 × 8 | 8 | 1,040 (1,280) | 2,000 mB | 992 mB/min | 2.0 min |
| 9 × 9 × 12 | 27 | 3,240 (4,320) | 6,750 mB | 3,780 mB/min | 1.8 min |

For long flights, pipe an onboard reserve tank to a drive inlet, as described in [supplying engines on board](version-3.0.md#supplying-engines-and-observers-on-board). Fuel adds only 0.4 weight units per 1,000 mB.

A drive saved with more fuel than its new tank holds keeps that fuel and burns it normally. Its inlets accept fuel again once the level falls below capacity, and the front gauge shows full until then.

### How many drives?

More drives raise the ceiling and top speed, but they also add weight and burn more fuel. Range is best with the fewest drives that still fly. A trip gets faster with each added drive until the craft can reach its cruise altitude; beyond that, extra drives mostly burn fuel. The calculator's tuning lab plots both for your build.

## Thrusters

Hover and Taxi speed now depend on the vehicle thrusters fitted: each **Rocket Thruster, Ion Drive, Plasma Vent or Impulse Engine block moves 200 units of gross weight** at full speed. With fewer thrusters than that, Hover slows in proportion to the square root of the shortfall, down to **35%** of 16 blocks per second. Sprint still adds 25%. Powered Taxi speed scales between 6 and 8 blocks per second the same way. A craft still needs at least one thruster to Hover.

In Flight, each of those thrusters also adds **0.08 thrust units** (about 82 weight units at takeoff), with no fuel. Several thrusters can lift a light craft without a Maxwell Drive, but a craft flying on thrusters alone stays below the seed view (Y=318) and flies at no more than 72 kb/h. With a Maxwell Drive the thrusters simply add to its thrust.

Craft without Maxwell Drives or three lift blocks have no sampled weight, so they keep the full Taxi speed.

[All version guides](README.md)
