package com.example.instrumentawdprobe;

import android.location.Location;
import android.util.Log;

import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetSocketAddress;
import java.net.SocketTimeoutException;

/** Receives authenticated phone coordinates on the local Wi-Fi network. */
final class PhoneLocationReceiver {
    interface Listener {
        void onPhoneLocation(Location location);
        void onPhoneLocationError(String detail);
    }

    private static final String TAG = "PhoneLocationReceiver";
    private final Listener listener;
    private volatile boolean running;
    private volatile DatagramSocket socket;
    private Thread thread;
    private String token;
    private String lastSession;
    private long lastSequence = -1L;

    PhoneLocationReceiver(Listener listener) {
        this.listener = listener;
    }

    synchronized void start(String pairingToken) {
        if (running) return;
        token = pairingToken;
        lastSession = null;
        lastSequence = -1L;
        running = true;
        thread = new Thread(new Runnable() {
            @Override public void run() { receiveLoop(); }
        }, "phone-gps-udp");
        thread.start();
    }

    synchronized void stop() {
        running = false;
        DatagramSocket current = socket;
        if (current != null) current.close();
        socket = null;
        thread = null;
        lastSession = null;
        lastSequence = -1L;
    }

    private void receiveLoop() {
        try (DatagramSocket receiver = new DatagramSocket(null)) {
            receiver.setReuseAddress(true);
            receiver.bind(new InetSocketAddress(PhoneLocationPacket.PORT));
            receiver.setSoTimeout(1000);
            socket = receiver;
            Log.i(TAG, "Listening for phone GPS on UDP " + PhoneLocationPacket.PORT);
            byte[] buffer = new byte[1024];
            while (running) {
                DatagramPacket datagram = new DatagramPacket(buffer, buffer.length);
                try {
                    receiver.receive(datagram);
                    PhoneLocationPacket.Sample sample = PhoneLocationPacket.decode(
                            datagram.getData(), datagram.getLength(), token);
                    if (!sample.session.equals(lastSession)) {
                        lastSession = sample.session;
                        lastSequence = -1L;
                    }
                    if (sample.sequence <= lastSequence) continue;
                    lastSequence = sample.sequence;
                    listener.onPhoneLocation(sample.toLocation());
                } catch (SocketTimeoutException ignored) {
                    // Allows stop() to be observed even if close() is delayed.
                } catch (SecurityException error) {
                    Log.w(TAG, "Dropped unauthenticated phone GPS packet");
                } catch (Exception error) {
                    if (running) Log.w(TAG, "Dropped invalid phone GPS packet", error);
                }
            }
        } catch (Exception error) {
            if (running) {
                Log.e(TAG, "Phone GPS receiver stopped", error);
                listener.onPhoneLocationError(error.getClass().getSimpleName());
            }
        } finally {
            socket = null;
        }
    }
}
