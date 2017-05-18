package edu.gmu.swe.knarr.server;

import java.net.ServerSocket;
import java.net.SocketException;

public class ConstraintServer {

	public static boolean STATELESS = Boolean.valueOf(System.getProperty("STATELESS","false"));
	public static ConstraintNode rootConstraintNode = new ConstraintNode(null);
    public static void main(String[] args) throws Exception {
    	 ServerSocket listener = new ServerSocket(9090);

    	 while (true) {
    		 try {
    			 new ConstraintServerHandler(listener.accept()).start();
    		 }catch(SocketException e)
    		 {
    			 //nop
    		 }
    		 catch (Throwable e) {
    			 System.err.println("Fatal exception in handler!!!");
    			 e.printStackTrace();
    			 listener.close();
    		 }
    	 }
    }

}
