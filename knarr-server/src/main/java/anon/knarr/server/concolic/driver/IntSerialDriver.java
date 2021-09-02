package anon.knarr.server.concolic.driver;


import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.ObjectOutputStream;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.LinkedList;

public class IntSerialDriver extends Driver<Integer[]> {

    public IntSerialDriver(ServerSocket listener, String host, int port) {
        super(listener, host, port);
    }

    @Override
    public Integer[] solution(int size) {
        return new Integer[size];
    }

    @Override
    public void interpret(Integer[] sol, int i, int val) {
        sol[i] = val;
    }

    @Override
    protected void sendData(Integer[] data, Socket s) throws IOException {
        try (ObjectOutputStream oos = new ObjectOutputStream(s.getOutputStream())) {
            for (int i = 0 ; i < data.length ; i++) {
                if (data[i] == null)
                    data[i] = 0;

                oos.writeInt(data[i]);
            }

            oos.flush();
        }
    }

    @Override
    public Integer[] fromFile(File f) throws IOException {
        LinkedList<Integer> ret = new LinkedList<>();
        try (DataInputStream dis = new DataInputStream(new FileInputStream(f))) {
            ret.addLast(dis.readInt());
        }

        return ret.toArray(new Integer[ret.size()]);
    }

    @Override
    public void toFile(Integer[] data, File f) throws IOException {
        try (DataOutputStream dos = new DataOutputStream(new FileOutputStream(f))) {
            for (int i = 0 ; i < data.length ; i++) {
                if (data[i] == null)
                    data[i] = 0;

                dos.writeInt(data[i]);
            }

            dos.flush();
        }
    }
}
