import java.io.BufferedInputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.EOFException;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import static java.lang.Math.round;
import java.net.ServerSocket;
import java.net.Socket;
import java.security.KeyStore;
import javax.net.ssl.KeyManagerFactory;
import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLServerSocketFactory;
import javax.net.ssl.SSLSocket;


public class Server extends Thread {
	
        public static final int BUFFSIZE = 1024;
        public final static String DOWNLOADS = "C:/Users/Tom/Documents";

        
	private ServerSocket ss;
	
	public Server(int port) {
		try {
                    SSLContext context;
                    KeyManagerFactory kmf;
                    KeyStore ks;
                    
                    char[] storepass = "password".toCharArray();
                    char[] keypass = "password".toCharArray();
                    String storepath = "C:/Users/Tom/OneDrive/Documents/NetBeansProjects/secure-file-server/src/ssl/server.pks";

                    context = SSLContext.getInstance("TLS");
                    kmf = KeyManagerFactory.getInstance("SunX509");
                    ks = KeyStore.getInstance("JKS");
                    ks.load(new FileInputStream(storepath), storepass);

                    kmf.init(ks, keypass);
                    context.init(kmf.getKeyManagers(), null, null);
                    SSLServerSocketFactory ssf = context.getServerSocketFactory();

                    ss = ssf.createServerSocket(port);
                    
		} catch (Exception e) {
			e.printStackTrace();
		}
	}
	
	public void run() {
                
		while (true) {
                        System.out.println("Waiting for connection...");
			try {
				SSLSocket clientSock = (SSLSocket) ss.accept();
                                System.out.println("Connected to client on " + clientSock.getRemoteSocketAddress() + ':' + clientSock.getLocalPort());
				serveClient(clientSock);
			} catch (IOException e) {
				e.printStackTrace();
			}
		}
	}

	private void saveFile(SSLSocket clientSock) throws IOException {
		DataInputStream dis = new DataInputStream(clientSock.getInputStream());
		byte[] buffer = new byte[BUFFSIZE];

                int filesize =  0;
                
            try {
                filesize = dis.readInt();
            } catch (EOFException e) {
                System.err.println("No filesize received from client");
                return;
            }
               
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
		//dis.close();
	}
        
        private void serveClient(SSLSocket sock) throws IOException {
            DataInputStream dis = new DataInputStream(sock.getInputStream());
            byte[] buffer = new byte[BUFFSIZE];
            dis.read(buffer, 0, BUFFSIZE);
            
            if(buffer[0] == 0) {
                System.out.println("No request received from client.");
                return;
            }
            String request = new String(buffer).split("\0")[0];
            String[] requestArgs = request.split(" ");
            System.out.println("Request is " + request);
            switch (requestArgs[0]) {
                case "add":
                        saveFile(sock);
                        break;
                case "fetch":
                        sendFile(sock, DOWNLOADS + '/' + requestArgs[1]);
                        break;
                case "list":  
                        sendList(sock);
                        break;
                case "vouch":
                        //todo add vouch function
                        break;
                default: System.out.println("Error request not valid.");
                         break;
            }
        }
        
        void sendList(SSLSocket clientSock) {
            
        }
        
        private byte[] intToByteArray(int value) {
            return new byte[] {
                (byte)(value >>> 24),
                (byte)(value >>> 16),
                (byte)(value >>> 8),
                (byte)value};
        }
        
        private void sendFile(SSLSocket clientSock, String path) throws IOException {
                File myFile = new File(path);  
                byte[] mybytearray = new byte[(int) myFile.length()];  

                FileInputStream fis = new FileInputStream(myFile);  
                BufferedInputStream bis = new BufferedInputStream(fis);  
                //bis.read(mybytearray, 0, mybytearray.length);  

                DataInputStream dis = new DataInputStream(bis);     
                dis.readFully(mybytearray, 0, mybytearray.length);  


                //Sending file name and file size to the server  
                DataOutputStream dos = new DataOutputStream(clientSock.getOutputStream());     
                int fileSize = mybytearray.length;
                dos.write(intToByteArray(fileSize), 0, 4);
                
                String fileName = myFile.getName() + new String(new char[BUFFSIZE]);
                
                
                System.out.println(fileName + " is "+ fileSize + " long");
                dos.write(fileName.getBytes(), 0, BUFFSIZE);     
                
                dos.write(mybytearray, 0, mybytearray.length);     
                dos.flush();  
                System.out.println("File sent!\n");
                        
                        

                //Sending file data to the server  
//                os.write(mybytearray, 0, mybytearray.length);  
  //              os.flush();  

                //Closing socket
                //os.close();
                dos.close();           
        }
	
	public static void main(String[] args) {
		Server fs = new Server(1342);
		fs.start();
	}

}