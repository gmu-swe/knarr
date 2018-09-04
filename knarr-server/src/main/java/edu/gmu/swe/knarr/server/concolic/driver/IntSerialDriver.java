package edu.gmu.swe.knarr.server.concolic.driver;


import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.ObjectOutputStream;
import java.io.OutputStreamWriter;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.LinkedList;

public class IntSerialDriver extends Driver {

    public IntSerialDriver(ServerSocket listener, String host, int port) {
        super(listener, host, port);
    }

    @Override
    protected void sendData(Object data, Socket s) throws IOException {
        try (ObjectOutputStream oos = new ObjectOutputStream(s.getOutputStream())) {
            oos.writeObject(data);
            oos.flush();
        }
    }

    @Override
    public Object fromFile(File f) throws IOException {
        LinkedList<Integer> ret = new LinkedList<>();
        try (BufferedReader br = new BufferedReader(new InputStreamReader(new FileInputStream(f))))  {
            String s;
            while (!"".equals(s = br.readLine()))
                ret.addLast(Integer.parseInt(s));
        }

        return ret.toArray(new Integer[ret.size()]);
    }

    @Override
    public void toFile(Object data, File f) throws IOException {
        try (OutputStreamWriter osw = new OutputStreamWriter(new FileOutputStream(f))) {
            for (Integer i : (Integer[])data) {
                osw.write(i);
                osw.write("\n");
            }
            osw.write("\n");
        }
    }
}
