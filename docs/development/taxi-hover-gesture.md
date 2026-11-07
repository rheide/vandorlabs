# Taxi/Hover jump gesture

Forge 1.12.2 already toggles Taxi/Hover on two rising jump-key edges within seven player ticks. Holding jump does not generate another edge. The existing FLIGHT packet remains subject to server driver, dedicated-controller, lift, thruster, landing-clearance and landing-gear checks. Native Vehicle Mode remains deferred.

The client now clears a pending tap when the world, player or ridden entity changes, the player is no longer the driver, or a GUI is open. Tick intervals must also be nonnegative. This prevents an unrelated later press from completing an old gesture; no server admission or packet format changed.

Focused actual-client coverage drives the real key binding and FLIGHT network handler, checking both server and synchronized client mode. Single press, held jump and both toggle directions passed before the correction. Opening and closing a nonpausing GUI between presses incorrectly toggled Hover before the change; the same case passes afterward. Java 8 standard assembly and dedicated-server linkage passed. Additional context-transition and refusal coverage is tracked separately from this first focused checkpoint.

Run the isolated utility fixture with `VANDOR_TAXI_HOVER_GESTURE_ONLY=true` and `testclient/test_viewscreen.sh --focus utility26` against the standard JAR. This is a focused regression, not a release gate.
