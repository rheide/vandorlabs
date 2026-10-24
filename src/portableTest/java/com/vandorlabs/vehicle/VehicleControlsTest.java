package com.vandorlabs.vehicle;

/** Command acceptance and prediction contracts shared by both platform adapters. */
public final class VehicleControlsTest {
    private static void expect(boolean value, String message) {
        if (!value) throw new AssertionError(message);
    }

    public static void main(String[] args) {
        VehicleControls controls = new VehicleControls();
        expect(controls.acknowledged() == -1 && controls.poll() == null, "initial acknowledgement");
        expect(!controls.offer(0, (byte) 2, (byte) 0, false), "invalid forward rejected");
        expect(!controls.offer(0, (byte) 0, (byte) -2, false), "invalid strafe rejected");
        expect(controls.offer(0, (byte) 1, (byte) -1, true), "invalid input must not consume sequence");
        expect(!controls.offer(0, (byte) 0, (byte) 0, false), "duplicate rejected");
        for (int i = 1; i < VehicleControls.LIMIT; i++) {
            expect(controls.offer(i, (byte) 0, (byte) 1, false), "queue capacity");
        }
        expect(!controls.offer(64, (byte) 0, (byte) 0, false), "bounded queue");
        expect(controls.acknowledged() == -1, "received is not applied");
        VehicleControls.Command first = controls.poll();
        expect(first.sequence == 0 && first.forward == 1 && first.strafe == -1 && first.brake, "command payload");
        expect(controls.acknowledged() == 0, "poll acknowledges");
        expect(controls.offer(64, (byte) 0, (byte) 0, false), "full rejection must not consume sequence");
        for (int i = 1; i <= 64; i++) expect(controls.poll().sequence == i, "FIFO ordering");
        expect(controls.poll() == null && controls.acknowledged() == 64, "empty poll retains acknowledgement");

        controls.clear();
        expect(controls.acknowledged() == -1, "clear resets acknowledgement");
        expect(controls.offer(0, (byte) 1, (byte) 0, false), "clear resets receive sequence");
        expect(!controls.offerLatest(1, (byte) 2, (byte) 0, false) && controls.size() == 1, "invalid latest keeps queue");
        expect(controls.offerLatest(1, (byte) -1, (byte) 1, true), "latest accepted");
        expect(controls.size() == 1 && controls.poll().sequence == 1, "hover coalesces burst");
        expect(!controls.offerLatest(0, (byte) 0, (byte) 0, false), "stale latest rejected");

        expect(VehicleControls.yawChange(1, 0.2, true, true) == 0, "hover autopilot yaw");
        expect(VehicleControls.yawChange(1, 0, true, false) == 1.7F, "hover stationary yaw");
        expect(VehicleControls.yawChange(1, -0.2, true, false) == -1.7F, "hover reverse yaw");
        expect(VehicleControls.yawChange(1, 0, false, false) == 0, "wheeled stationary steering");
        expect(VehicleControls.yawChange(0, .28, false, false) == 0, "straight travel");
        expect(Math.abs(VehicleControls.yawChange(1, .28, false, false) - 1.694F) < .000001F,
                "full-speed steering fixture");
        expect(Math.abs(VehicleControls.yawChange(1, .14, false, false) - 1.1935F) < .000001F,
                "half-speed steering fixture");
        expect(Math.abs(VehicleControls.yawChange(1, -.28, false, false) + 1.694F) < .000001F,
                "reverse follows travel");
        expect(Math.abs(VehicleControls.yawChange(-1, .28, false, false) + 1.694F) < .000001F,
                "opposite steering");
        System.out.println("Vehicle controls PASS: bounded FIFO, latest state, acknowledgements and yaw parity");
    }
}
