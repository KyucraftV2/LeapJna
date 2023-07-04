package komposten.leapjna.example;

import komposten.leapjna.leapc.LeapC;
import komposten.leapjna.leapc.data.*;
import komposten.leapjna.leapc.enums.eLeapEventType;
import komposten.leapjna.leapc.enums.eLeapPolicyFlag;
import komposten.leapjna.leapc.enums.eLeapRS;
import komposten.leapjna.leapc.events.*;

import java.io.FileWriter;
import java.io.IOException;
import java.io.PrintWriter;
import java.util.Arrays;

public class TestClass {
    private static final LSL.StreamOutlet outletPaume;
    private static final LSL.StreamOutlet outletPoignet;
    private static final LSL.StreamOutlet outletMajeurDistal;
    private static final LSL.StreamOutlet outletMajeurIntermediate;

    static {
        try {
            outletPaume = createOutlet("Paume", "MoCap", 3, 120);
            outletPoignet = createOutlet("Poignet", "MoCap", 3, 120);
            outletMajeurDistal = createOutlet("MajeurFin", "MoCap", 3, 120);
            outletMajeurIntermediate = createOutlet("MajeurMilieu", "MoCap", 3, 120);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    public TestClass() throws IOException {
    }

    public static LSL.StreamOutlet createOutlet(String name, String type, int channel_count, double srate) throws IOException {
        LSL.StreamInfo info = new LSL.StreamInfo(name, type, channel_count, srate, LSL.ChannelFormat.float32, name);
        return new LSL.StreamOutlet(info);

    }

    public static void main(String[] args) {
        LEAP_CONNECTION leapConnection = new LEAP_CONNECTION();

        //Create connection
        LeapC.INSTANCE.LeapCreateConnection(null, leapConnection);
        eLeapRS result = LeapC.INSTANCE.LeapOpenConnection(leapConnection.handle);

        if (result == eLeapRS.Success) {
            doPollLoop(leapConnection);

        }

    }

    public static void doPollLoop(LEAP_CONNECTION leapConnection) {
        boolean firstIteration = true;

        eLeapRS previousResult = null;
        while (true) {
            LEAP_CONNECTION_MESSAGE message = new LEAP_CONNECTION_MESSAGE();
            eLeapRS result = LeapC.INSTANCE.LeapPollConnection(leapConnection.handle, 30,
                    message);

            if (firstIteration) {
                // Enable the images and pause policies
                LeapC.INSTANCE.LeapSetPolicyFlags(leapConnection.handle, eLeapPolicyFlag
                        .createMask(eLeapPolicyFlag.Images, eLeapPolicyFlag.AllowPauseResume), 0);

                firstIteration = false;
            }

            if (result == eLeapRS.Timeout) {
                if (previousResult != eLeapRS.Timeout) {
                    System.out.println("Timeout");
                }
            } else if (result != eLeapRS.Success) {
                System.out.println(message.getType());
            }

            boolean handled = handleInformationalEvents(message, leapConnection);

            if (!handled) {
                if (message.getType() == eLeapEventType.Tracking) {
                    handleFrame(message.getTrackingEvent());
                } else if (message.getType() == eLeapEventType.Image) {
                    handleImage(message.getImageEvent());
                }
            }

            previousResult = result;

            if (Thread.interrupted()) {
                break;
            }
        }

    }

    private static boolean handleInformationalEvents(LEAP_CONNECTION_MESSAGE message,
                                                     LEAP_CONNECTION leapConnection) {
        boolean handled = true;

        switch (message.getType()) {
            case Connection:
                handleConnectionEvent(message.getConnectionEvent());
                break;
            case ConnectionLost:
                handleConnectionLostEvent();
                break;
            case Device:
                handleDeviceEvent(message.getDeviceEvent());
                break;
            case DeviceStatusChange:
                handleDeviceStatusChangeEvent(message.getDeviceStatusChangeEvent());
                break;
            case DeviceLost:
                handleDeviceLostEvent(message.getDeviceLostEvent());
                break;
            case DeviceFailure:
                handleDeviceFailureEvent(message.getDeviceFailureEvent());
                break;
            case Policy:
                handlePolicyEvent(message.getPolicyEvent(), leapConnection);
                break;
            case ConfigChange:
                handleConfigChangeEvent(message.getConfigChangeEvent());
                break;
            case ConfigResponse:
                handleConfigResponseEvent(message);
                break;
            case LogEvent:
                handleLogEvent(message.getLogEvent());
                break;
            case LogEvents:
                handleLogEvents(message.getLogEvents());
                break;
            default:
                handled = false;
                break;
        }

        return handled;
    }

    private static void handleConnectionEvent(LEAP_CONNECTION_EVENT event) {
        System.out.printf("Connection flags: %s%n", event.getFlags());
    }


    private static void handleConnectionLostEvent() {
        System.out.println("Connection lost!");
    }


    private static void handleDeviceEvent(LEAP_DEVICE_EVENT event) {
        eLeapRS result;

        // Print device info.
        System.out.println("Device detected");
        System.out.printf("  Id: %d%n", event.device.id);

        // Open the device to retrieve information about it.
        LEAP_DEVICE phDevice = new LEAP_DEVICE();
        result = LeapC.INSTANCE.LeapOpenDevice(event.device, phDevice);

        if (result == eLeapRS.Success) {
            // Read the length of the device's serial string
            LEAP_DEVICE_INFO info = new LEAP_DEVICE_INFO();
            result = LeapC.INSTANCE.LeapGetDeviceInfo(phDevice.handle, info);

            if (result == eLeapRS.InsufficientBuffer || result == eLeapRS.Success) {
                // Allocate space for the serial and read device info
                info.allocateSerialBuffer(info.serial_length);
                result = LeapC.INSTANCE.LeapGetDeviceInfo(phDevice.handle, info);

                if (result == eLeapRS.Success) {
                    System.out.printf("  Status: %s%n", Arrays.toString(info.getStatus()));
                    System.out.printf("  Baseline: %d \u00b5m%n", info.baseline);
                    System.out.printf("  FoV: %.02f\u00b0 x %.02f\u00b0 (HxV)%n",
                            Math.toDegrees(info.h_fov), Math.toDegrees(info.v_fov));
                    System.out.printf("  Range: %d \u00b5m%n", info.range);
                    System.out.printf("  Serial: %s%n", info.serial);
                    System.out.printf("  Product ID: %s (%d)%n",
                            LeapC.INSTANCE.LeapDevicePIDToString(info.pid), info.pid);
                    System.out.printf("  Capabilities: %s%n",
                            Arrays.toString(info.getCapabilities()));

                    LeapC.INSTANCE.LeapCloseDevice(phDevice.handle);
                } else {
                    System.out.printf("Failed to read device info: %s%n", result);
                }
            } else {
                System.out.printf("Failed to read device info to get serial length: %s%n", result);
            }
        } else {
            System.out.printf("Failed to open device: %s%n", result);
        }

    }


    private static void handleDeviceStatusChangeEvent(LEAP_DEVICE_STATUS_CHANGE_EVENT event) {
        System.out.printf("Device status changed: %d | From %s to %s%n",
                event.device.id, Arrays.toString(event.getLastStatus()),
                Arrays.toString(event.getStatus()));
    }


    private static void handlePolicyEvent(LEAP_POLICY_EVENT event,
                                          @SuppressWarnings("unused") LEAP_CONNECTION leapConnection) {
        if (Arrays.stream(event.getCurrentPolicy()).anyMatch(p -> p == eLeapPolicyFlag.Images)) {
            System.out.println("Images allowed");
        }
    }


    private static void handleLogEvent(LEAP_LOG_EVENT event) {
        System.out.printf("[LOG] %s: %s%n", event.getSeverity(),
                event.message);
    }


    private static void handleLogEvents(LEAP_LOG_EVENTS events) {
        for (LEAP_LOG_EVENT logEvent : events.getEvents()) {
            handleLogEvent(logEvent);
        }
    }


    private static void handleDeviceFailureEvent(LEAP_DEVICE_FAILURE_EVENT event) {
        System.out.printf("Device failure: %s%n",
                Arrays.toString(event.getStatus()));
    }


    private static void handleDeviceLostEvent(LEAP_DEVICE_EVENT event) {
        System.out.printf("Device was lost:%n");
        System.out.printf("  Id: %d%n", event.device.id);
        System.out.printf("  Status: %b%n",
                Arrays.toString(event.getStatus()));
    }


    private static void handleConfigChangeEvent(LEAP_CONFIG_CHANGE_EVENT event) {
        System.out.printf("Result of config change request %d: %s%n", event.requestID,
                event.status);
    }


    private static void handleConfigResponseEvent(LEAP_CONNECTION_MESSAGE message) {
        LEAP_CONFIG_RESPONSE_EVENT responseEvent = message.getConfigResponseEvent();
        System.out.println("Images allowed: " + (responseEvent.value.getInt() == 2));
    }


    private static void handleFrame(LEAP_TRACKING_EVENT trackingEvent) {
        LEAP_HAND[] hand = trackingEvent.getHands();
        if (hand.length > 0) {
            LEAP_PALM palm = hand[0].palm;
            LEAP_BONE dernierePhalange = hand[0].digits.middle.distal;
            LEAP_BONE phalangeIntermediaire = hand[0].digits.middle.intermediate;
            LEAP_BONE poignet = hand[0].arm;
            System.out.println(palm.position.x + " " + palm.position.y + " " + palm.position.z);
            float[] samplePaume = {palm.position.x, palm.position.y, palm.position.z};
            float[] sampleDistal = {dernierePhalange.next_joint.x, dernierePhalange.next_joint.y, dernierePhalange.next_joint.z};
            float[] samplePoignet = {poignet.next_joint.x, poignet.next_joint.y, poignet.next_joint.z};
            float[] sampleIntermediate = {phalangeIntermediaire.next_joint.x,phalangeIntermediaire.next_joint.y,phalangeIntermediaire.next_joint.z};
            outletPaume.push_sample(samplePaume);
            outletMajeurDistal.push_sample(sampleDistal);
            outletPoignet.push_sample(samplePoignet);
            outletMajeurIntermediate.push_sample(sampleIntermediate);
        }
    }


    private static void handleImage(LEAP_IMAGE_EVENT imageEvent) {

    }
}
