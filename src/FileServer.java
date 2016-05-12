import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.ServerSocket;
import java.net.Socket;

public class FileServer {

  public final static int SOCKET_PORT = 1342;  // you may change this
  public final static String DOWNLOADS = "c:/users/Tom/desktop";  // you may change this
  public final static int FILE_SIZE = 6022386;
  
  public static void main (String [] args ) throws IOException {
    int bytesRead;
    int current = 0;
    ByteArrayOutputStream baos = new ByteArrayOutputStream();
    
    ServerSocket serverSocket = null;
    serverSocket = new ServerSocket(SOCKET_PORT);
    
   System.out.println("Waiting");
   
     while(true) {  
        Socket clientSocket = null;  
        clientSocket = serverSocket.accept();  
        System.out.println("Just connected to " + clientSocket.getRemoteSocketAddress());   
        InputStream in = clientSocket.getInputStream();  
           
        DataInputStream clientData = new DataInputStream(in);   
           
        String fileName = clientData.readUTF();
        String filePath = DOWNLOADS + "/" +  fileName;
        
        OutputStream output = new FileOutputStream(filePath);     
        long size = clientData.readLong();     
        byte[] buffer = new byte[1024];     
        while (size > 0 && (bytesRead = clientData.read(buffer, 0, (int)Math.min(buffer.length, size))) != -1)     
        {     
            output.write(buffer, 0, bytesRead);     
            size -= bytesRead;     
        }  
           
        // Closing the FileOutputStream handle
        in.close();
        clientData.close();
        output.close();  
     }
  }
}
