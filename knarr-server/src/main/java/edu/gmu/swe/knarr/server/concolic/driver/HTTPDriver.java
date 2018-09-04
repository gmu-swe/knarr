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

public class HTTPDriver extends Driver {

    public HTTPDriver(ServerSocket listener, String host, int port) {
        super(listener, host, port);
    }

    @Override
    protected void sendData(Object data, Socket s) throws IOException {
        String toSend = new String((byte[])data) + "\n\n";

        try (BufferedWriter bw =  new BufferedWriter(new OutputStreamWriter(s.getOutputStream()))) {
            bw.write(toSend);
            bw.flush();
        }
    }

    @Override
    public Object fromFile(File f) throws IOException {
        return Files.readAllBytes(f.toPath());
    }

    @Override
    public void toFile(Object data, File f) throws IOException {
        URI uri = f.toURI();
        try (FileOutputStream fos = new FileOutputStream(new File(uri))) {
            fos.write((byte[])data);
        }
    }
}
