package edu.gmu.swe.knarr.server.concolic.driver;

import edu.gmu.swe.knarr.server.ConstraintServerHandler;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.InterruptedIOException;
import java.net.ServerSocket;
import java.net.Socket;

public abstract class Driver {

    private final ServerSocket listener;
    private final String host;
    private final int port;

    public Driver(ServerSocket listener, String host, int port) {
        this.listener = listener;
        this.host = host;
        this.port = port;
    }

    public final ConstraintServerHandler drive(Object in) throws IOException {
        // Connect to the server
        try(Socket s = new Socket(host, port)) {
            s.setSoTimeout(2000);

            sendData(in, s);

            ConstraintServerHandler ret;
            try {
                ret = receiveConstraints();
            } catch (InterruptedIOException e) {
                // TODO do something with the timeout input
                return null;
            }

            try {
                receiveResult(s);
                // TODO do something with the result
            } catch (IOException e) {
                // Don't care
            }

            return ret;
        }
    }

    protected abstract void sendData(Object data, Socket s) throws IOException;

    public abstract void toFile(Object data, File f) throws IOException;

    public abstract Object fromFile(File f) throws IOException;

    protected ConstraintServerHandler receiveConstraints() throws IOException {
        try (Socket skt = listener.accept()) {
            return new ConstraintServerHandler(skt);
        }
    }

    protected void receiveResult(Socket s) throws IOException {
        try (BufferedReader br = new BufferedReader(new InputStreamReader(s.getInputStream()))) {
            String res = br.readLine();
        }
    }

}
