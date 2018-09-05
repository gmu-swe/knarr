package edu.gmu.swe.knarr.server.concolic.driver;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.URI;
import java.nio.file.Files;

public class HTTPDriver extends Driver<byte[]> {

    public HTTPDriver(ServerSocket listener, String host, int port) {
        super(listener, host, port);
    }

    @Override
    public byte[] solution(int size) {
        return new byte[size];
    }

    @Override
    public void interpret(byte[] solution, int i, int val) {
        solution[i] = new Integer(val).byteValue();
    }

    @Override
    protected void sendData(byte[] data, Socket s) throws IOException {
        String toSend = new String(data) + "\n\n";
        try (BufferedWriter bw =  new BufferedWriter(new OutputStreamWriter(s.getOutputStream()))) {
            bw.write(toSend);
            bw.flush();
        }
    }

    @Override
    public byte[] fromFile(File f) throws IOException {
        return Files.readAllBytes(f.toPath());
    }

    @Override
    public void toFile(byte[] data, File f) throws IOException {
        URI uri = f.toURI();
        try (FileOutputStream fos = new FileOutputStream(new File(uri))) {
            fos.write(data);
        }
    }
}
