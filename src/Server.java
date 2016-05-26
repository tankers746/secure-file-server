//<<<<<<< HEAD
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
import java.net.URL;


public class Server extends Thread {
	
    public static final int BUFFSIZE = 1024;
    public final static String DOWNLOADS = "C:/Users/Waleed/Documents";
    private FileTable fileTable;

        
	private ServerSocket ss;
	
	public Server(int port) {
		try {
			ss = new ServerSocket(port);
		} catch (IOException e) {
			e.printStackTrace();
		}
		this.fileTable = new FileTable();
	}
	
	public void run() {
//        createPath("file1.txt");
		while (true) {
            System.out.println("Waiting for connection...");
			try {
				Socket clientSock = ss.accept();
                System.out.println("Connected to client on " + clientSock.getRemoteSocketAddress() + ':' + clientSock.getLocalPort());
                getCommand(clientSock);
//				saveFile(clientSock);
//                sendFile(clientSock,"C:/Users/Tom/Desktop/audio-vga (6).m4v");
			} catch (IOException e) {
				e.printStackTrace();
			}
		}
	}

	private String getCommand(Socket clientSock) throws IOException{
		DataInputStream dis = new DataInputStream(clientSock.getInputStream());
		char command = dis.readChar();
		switch (command){
			case 'a':{
				saveFile(clientSock);
			}
			case 'c':{
				int circumference = dis.readInt();
				setCircumference(clientSock, circumference);
			}
			case 'f':{
				String filename = dis.readUTF();
				try{
					sendFile(clientSock, createPath(filename));
				} catch (IOException e){
					e.printStackTrace();
				}
			}
			case 'l':{
				sendList(clientSock);
			}
			case 'n':{
				String pname = dis.readUTF();
				setPersonInCircle(clientSock, pname);
			}
			case 'u':{
				saveFile(clientSock);
			}
			case 'v':{
				String filename = dis.readUTF();
				saveFile(clientSock); //The certificate's a file?
				setCertificateForFile(clientSock, filename);
			}
			default:{
				dis.readUTF();
				break;
			}
		}
		return "";
	}
	
	private void setCertificateForFile(Socket clientSock, String filename){
		
	}
	
	private void setPersonInCircle(Socket clientSock, String pname){
		
	}
	
	private void sendList(Socket clientSock){
		
	}
	
	private String createPath(String filename){
		URL loc = Server.class.getProtectionDomain().getCodeSource().getLocation();
		String cloc = loc.toString();
		String path = cloc + "filestore";
		System.out.println(path);
		return path;
	}
	
	private void setCircumference(Socket clientSock, int circ){
		
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
		//dis.close();
	}
        
        public byte[] intToByteArray(int value) {
            return new byte[] {
                (byte)(value >>> 24),
                (byte)(value >>> 16),
                (byte)(value >>> 8),
                (byte)value};
        }
        
        private void sendFile(Socket clientSock, String path) throws IOException {
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
                dis.close();
                dos.close();           
        }
	
	public static void main(String[] args) {
		Server fs = new Server(1342);
		fs.start();
	}
}