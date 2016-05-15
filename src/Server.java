import java.io.BufferedInputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import static java.lang.Math.round;
import java.net.ServerSocket;
import java.net.Socket;


public class Server extends Thread {
	
        public static final int BUFFSIZE = 1024;
        public final static String DOWNLOADS = "C:/Users/Tom/Documents";

        
	private ServerSocket ss;
	
	public Server(int port) {
		try {
			ss = new ServerSocket(port);
		} catch (IOException e) {
			e.printStackTrace();
		}
	}
	
	public void run() {
                
		while (true) {
                        System.out.println("Waiting for connection...");
			try {
				Socket clientSock = ss.accept();
                                System.out.println("Connected to client on " + clientSock.getRemoteSocketAddress() + ':' + clientSock.getLocalPort());
				//saveFile(clientSock);
                                sendFile(clientSock,"C:/Users/Tom/Desktop/test.pdf");
			} catch (IOException e) {
				e.printStackTrace();
			}
		}
	}

	private void saveFile(Socket clientSock) throws IOException {
		DataInputStream dis = new DataInputStream(clientSock.getInputStream());
		byte[] buffer = new byte[BUFFSIZE];

                int filesize =  0;
                filesize = dis.readInt();
                System.out.println("Filesize is " + filesize);
                dis.read(buffer, 0, BUFFSIZE);
                String fileName = new String(buffer).split("\0")[0];
                System.out.println("Filename is " + fileName);

                
                int read = 0;
		int totalRead = 0;
		int remaining = filesize;
                
                File receivedFile = new File(DOWNLOADS + '/' + fileName);
		FileOutputStream fos = new FileOutputStream(receivedFile);                
                //String progress = "[                    ]";
                int n = 0;
		while((read = dis.read(buffer, 0, Math.min(buffer.length, remaining))) > 0) {
			totalRead += read;
			remaining -= read;
			//System.out.println("read " + totalRead + " bytes.");
			fos.write(buffer, 0, read);
                        
                        //System.out.println(round(((float)totalRead/(float)filesize)*100));
                        if(round(((float)totalRead/(float)filesize)*100) == n) {
                            n = n+5;
                            System.out.print('|');
                        }
                        
		}
                System.out.println("\nFile saved in " + DOWNLOADS + '/' + fileName);
		
		fos.close();
		dis.close();
	}
        
        private void sendFile(Socket clientSock, String path) throws IOException {
                File myFile = new File(path);  
                byte[] mybytearray = new byte[(int) myFile.length()];  

                FileInputStream fis = new FileInputStream(myFile);  
                BufferedInputStream bis = new BufferedInputStream(fis);  
                //bis.read(mybytearray, 0, mybytearray.length);  

                DataInputStream dis = new DataInputStream(bis);     
                dis.readFully(mybytearray, 0, mybytearray.length);  

                OutputStream os = clientSock.getOutputStream();  

                //Sending file name and file size to the server  
                DataOutputStream dos = new DataOutputStream(os);     
                int fileSize = mybytearray.length;
                dos.writeInt(fileSize);
                
                String fileName = myFile.getName() + new String(new char[BUFFSIZE]);
                
                
                System.out.println(fileName + " is "+ fileSize + " long");
                dos.write(fileName.getBytes(), 0, BUFFSIZE);     
                
                dos.write(mybytearray, 0, mybytearray.length);     
                dos.flush();  

                //Sending file data to the server  
                os.write(mybytearray, 0, mybytearray.length);  
                os.flush();  

                //Closing socket
                os.close();
                dos.close();           
        }
	
	public static void main(String[] args) {
		Server fs = new Server(1342);
		fs.start();
	}

}