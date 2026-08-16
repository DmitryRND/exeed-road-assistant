package com.example.instrumentawdprobe;

import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.Locale;

/** Read-only access to the stock 12 V battery diagnostics exposed by Alfus RPC. */
final class BatteryRpcReader {
    private static final int GET_OPCODE = 1880;

    static final class Result {
        final double voltage;
        final int soc;
        final int soh;
        final byte[] voltageResponse;
        final byte[] stateResponse;

        Result(double voltage, int soc, int soh,
               byte[] voltageResponse, byte[] stateResponse) {
            this.voltage = voltage;
            this.soc = soc;
            this.soh = soh;
            this.voltageResponse = voltageResponse;
            this.stateResponse = stateResponse;
        }

        String displayText() {
            return String.format(Locale.US,
                    "12 В: %.2f V   Заряд: %d%%   Здоровье: %d%%",
                    voltage, soc, soh);
        }

        String diagnosticText() {
            return displayText()
                    + " voltageRaw=" + Arrays.toString(voltageResponse)
                    + " stateRaw=" + Arrays.toString(stateResponse);
        }
    }

    private BatteryRpcReader() { }

    static Result read() throws Exception {
        Class<?> managerClass = Class.forName("alfusos.rpc.RpcManager");
        Object manager = managerClass.getMethod("getInstance").invoke(null);
        if (manager == null) {
            throw new IllegalStateException("Alfus RPC service is unavailable");
        }
        Method getMessage = managerClass.getMethod(
                "getMessage", int.class, byte[].class);

        byte[] voltageResponse = request(manager, getMessage, 0x9F);
        byte[] stateResponse = request(manager, getMessage, 0x9E);

        requireLength("voltage", voltageResponse, 3);
        requireLength("SOC/SOH", stateResponse, 3);

        int rawVoltage = (voltageResponse[1] & 0xFF)
                | ((voltageResponse[2] & 0xFF) << 8);
        double voltage = rawVoltage * 0.0009765625d + 3.0d;
        int soc = stateResponse[1] & 0xFF;
        int soh = stateResponse[2] & 0xFF;
        return new Result(voltage, soc, soh, voltageResponse, stateResponse);
    }

    private static byte[] request(Object manager, Method getMessage,
                                  int subOpcode) throws Exception {
        byte[] request = new byte[25];
        request[0] = (byte) subOpcode;
        return (byte[]) getMessage.invoke(manager, GET_OPCODE, request);
    }

    private static void requireLength(String name, byte[] response, int minimum) {
        if (response == null || response.length < minimum) {
            throw new IllegalStateException(name + " response is "
                    + (response == null ? "null" : "only " + response.length + " bytes"));
        }
    }
}
