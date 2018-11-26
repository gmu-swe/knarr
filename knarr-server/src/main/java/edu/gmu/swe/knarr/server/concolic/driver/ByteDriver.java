package edu.gmu.swe.knarr.server.concolic.driver;

import java.io.*;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.URI;
import java.nio.file.Files;

public class ByteDriver extends Driver<byte[]> {
    public ByteDriver(ServerSocket listener, String host, int port) {
        super(listener, host, port);
    }

    @Override
    public byte[] solution(int size) {
        return new byte[size];
    }

    @Override
    public void interpret(byte[] solution, int i, int val) {
        solution[i] = (byte) val;
    }

    @Override
    protected void sendData(byte[] data, Socket s) throws IOException {
        try (BufferedOutputStream bos =  new BufferedOutputStream(s.getOutputStream())) {
            bos.write(data, 0, data.length);
            bos.flush();
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
