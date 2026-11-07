# Native Ramp Part client presentation

The generated-part host retains authoritative collision and processed belt timing. Its renderer consumes immutable part publications, with no original tile NBT, inventories or render-time world discovery. Ramp public activation remains separate from this client adapter.

`RampPartAnimation` uses the shared linear `ControllerPlatform.pose` rate and a pause-aware local visual clock. An operation starts from its observed pose and belt phase. New movement commands retarget the current cosmetic pose continuously; duplicate or older revisions do not restart it. A new stationary command installs its authoritative endpoint. Running belt speed/direction changes preserve the current cosmetic phase; a new stop installs the authoritative processed phase so stationary treads match their host. Duplicate stops and late old running commands cannot restart motion.

The focused scalar fixture passes on Java 8 and Java 21, including delayed observations, low server update frequency, reversals, pause/resume, time rewind, late stops and final endpoints. Its warmed 100,000-sample batch allocates zero bytes. This establishes only the scalar clock policy; placed-host rendering, material reload and moving mesh costs require their separate focused client checks.
