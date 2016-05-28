import java.util.*;
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
import java.net.SocketException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import static java.nio.file.StandardCopyOption.REPLACE_EXISTING;
import java.security.KeyStore;
import javax.net.ssl.KeyManagerFactory;
import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLServerSocketFactory;
import javax.net.ssl.SSLSocket;
import java.security.cert.*;
import javax.naming.ldap.LdapName;
import javax.naming.ldap.Rdn;


public class Server extends Thread {
	
        public static final int BUFFSIZE = 1024;
        public final static String DOWNLOADS = "Files";
        public final static String CERTSTORE = "Certificates";        
        public final static String STOREPATH = "server.jks"; 
        
	private ServerSocket ss;
        public static KeyStore ks;
	
	public Server(int port) {
		try {
                    SSLContext context;
                    KeyManagerFactory kmf;
                    
                    char[] storepass = "password".toCharArray();
                    char[] keypass = "password".toCharArray();
                    
                    /*Initialize and load the java keystore*/
                    context = SSLContext.getInstance("TLS");
                    kmf = KeyManagerFactory.getInstance("SunX509");
                    ks = KeyStore.getInstance("JKS");
                    ks.load(new FileInputStream(STOREPATH), storepass);
                    kmf.init(ks, keypass);
                    context.init(kmf.getKeyManagers(), null, null);
                    
                    /*Create an SSL socket*/
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

	private boolean saveFile(SSLSocket clientSock) throws IOException {
		DataInputStream dis = new DataInputStream(clientSock.getInputStream());
		byte[] buffer = new byte[BUFFSIZE];

                int filesize =  0;
                
            try {
                filesize = dis.readInt();
            } catch (EOFException e) {
                System.err.println("No filesize received from client");
                return false;
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
                if(totalRead < filesize) {
                    System.err.println("File transfer was unsuccessful.");
                    fos.close();
                    Path p = Paths.get(DOWNLOADS + '/' + fileName);
                    Files.delete(p); 
                    return false;
                } else {
                    System.out.println("File saved in " + DOWNLOADS + '/' + fileName);
                    fos.close();
                    return true;
                }
	}
        
        private void serveClient(SSLSocket sock) throws IOException {
            DataInputStream dis = new DataInputStream(sock.getInputStream());
            byte[] buffer = new byte[BUFFSIZE];
            dis.read(buffer, 0, BUFFSIZE);
            
            if(buffer[0] == 0) {
                System.err.println("No request received from client.");
                return;
            }
            String certOwner;
            boolean successful;
            String request = new String(buffer).split("\0")[0];
            String[] requestArgs = request.split(" ");
            System.out.println("Request is " + request);
            switch (requestArgs[0]) {
                case "add":
                        successful = saveFile(sock);
                        if(successful && requestArgs[1].equals("cert")) {
                            certOwner = processCert(requestArgs[2]);
                            if(certOwner == null) {
                                sendResult(sock, "ERROR: Unable to get cert owner");
                                return;
                            }
                            sendResult(sock, "The certificate '" + certOwner + "' has been added to the server");
                        } else if(successful && requestArgs[1].equals("file")) {
                            sendResult(sock, requestArgs[2] + " has been added to the server");
                        } else {
                            sendResult(sock, "ERROR: Unable to save file");
                        }
                        break;
                case "fetch":                        
                        successful = sendFile(sock, DOWNLOADS + '/' + requestArgs[1]);
                        if(successful) {
                            sendResult(sock, requestArgs[1] + " has been sent to the client");
                        } else {
                            sendResult(sock, "ERROR: Unable to send the file to the client");
                        }
                        break;
                case "list":  
                        successful = sendList(sock);
                        if(successful) {
                            sendResult(sock, "File list has been sent to the client");
                        } else {
                            sendResult(sock, "ERROR: Unable to send file list");
                        }
                        break;
                case "vouch":
                        if (!new File(DOWNLOADS + '/' + requestArgs[1]).exists()) {
                            sendResult(sock, "ERROR: File: " + requestArgs[1] + " does not exist on the server");
                            return;
                        }
                        successful = saveFile(sock);
                        if(successful) {
                            certOwner = processCert(requestArgs[2]);
                            if(certOwner == null) {
                                System.err.println("");
                                sendResult(sock, "ERROR: Unable to get cert owner");
                                return;
                            }
                            System.out.println("The certificate '" + certOwner + "' has been added to the server");
                            successful = vouchFile(requestArgs[1], certOwner);
                            if(successful) {
                                sendResult(sock, certOwner + " succesfuly vouched for file: " + requestArgs[1]);
                            } else {
                                sendResult(sock, "ERROR: Unable to vouch for " + requestArgs[1]);
                            }
                        }
                        sendResult(sock, "ERROR: Unable to save certificate.");
                        break;
                default: System.err.println("Error request not valid.");
                         break;
            }
        }
        
        String processCert(String certName) throws IOException {
            String owner = null;
            Path source = Paths.get(DOWNLOADS + '/' + certName);
            Path newdir = Paths.get(CERTSTORE);
            owner = getOwner(getCert(source.toString())); //gets the cert owner
            String newName = owner + ".cer";
            try {
                Files.move(source, newdir.resolve(newName), REPLACE_EXISTING);
            } catch(Exception e) {
                System.err.println("Moving certificate failed.");
            }
                return owner;
        }
        
        boolean vouchFile(String fileName, String certOwner) {
            //add certOwner to file hastable
            return true;
        }
        
        boolean sendList(SSLSocket clientSock) {
            return true;
        }
        
        void sendResult(SSLSocket clientSock, String msg) throws IOException {
            try {
                DataOutputStream dos = new DataOutputStream(clientSock.getOutputStream());     
                String message = msg + new String(new char[BUFFSIZE]);
                System.out.println(msg);
                dos.write(message.getBytes(), 0, BUFFSIZE);         
                dos.flush();  
                dos.close(); 
            } catch(Exception e) {
                return;    
            }
         
        }
        
        private byte[] intToByteArray(int value) {
            return new byte[] {
                (byte)(value >>> 24),
                (byte)(value >>> 16),
                (byte)(value >>> 8),
                (byte)value};
        }
        
        private boolean sendFile(SSLSocket clientSock, String path) throws IOException {
            File myFile = new File(path);  
            byte[] mybytearray = new byte[(int) myFile.length()];
            DataOutputStream dos = new DataOutputStream(clientSock.getOutputStream());     
            try {
                FileInputStream fis = new FileInputStream(myFile);  
                BufferedInputStream bis = new BufferedInputStream(fis);  
                DataInputStream dis = new DataInputStream(bis);     
                dis.readFully(mybytearray, 0, mybytearray.length);  
            } catch (Exception e) {
                System.err.println("Unable to open or read file.");
                dos.write(intToByteArray(0), 0, 4); //send a filesize of 0 so the client knows things are wrong
                return false;                
            }   
                try {
                    
                    //Sending file name and file size to the server  
                    int fileSize = mybytearray.length;
                    dos.write(intToByteArray(fileSize), 0, 4);                    
                    String fileName = myFile.getName() + new String(new char[BUFFSIZE]);
                    dos.write(fileName.getBytes(), 0, BUFFSIZE);     

                    //Sending file
                    dos.write(mybytearray, 0, mybytearray.length);     
                    dos.flush();  
                    //dos.close(); 
                    System.out.println("File sent!\n");
                    return true;
                        
                } catch(SocketException e) {
                    System.err.println("Lost connection to client during transfer.");
                    return false;
                }
                          
        }
	
        /*Get a certificate object from a .cer file*/       
        public static X509Certificate getCert(String certpath) {
                X509Certificate cert = null;
                try {
                        FileInputStream fis = new FileInputStream(certpath);
                        BufferedInputStream bis = new BufferedInputStream(fis);
                        CertificateFactory cf = CertificateFactory.getInstance("X.509");                        
                        while (bis.available() > 0) {
                                cert = (X509Certificate)cf.generateCertificate(bis); 
                                
                        }
                        fis.close();
                        bis.close();
		} catch (Exception e) {
			System.err.println("Could not get certificate.");
		}
                return cert;
        }
        
        /*Get the name of the certificate owner*/
        public static String getOwner(X509Certificate cert) {
                String owner = null;
                try {
                        String dn = cert.getSubjectX500Principal().getName();                
                        LdapName ldapDN = new LdapName(dn);
                        List<Rdn> rdn = ldapDN.getRdns();
                        owner = (String)rdn.get(5).getValue();
                } catch (Exception e) {
                        System.err.println("Could not get owner name.");
                }
                return owner;
        }

        /*Get the name of the certificate signer*/
        public static String getSigner(X509Certificate cert) {
                String owner = null;
                try {
                        String dn = cert.getIssuerX500Principal().getName();                
                        LdapName ldapDN = new LdapName(dn);
                        List<Rdn> rdn = ldapDN.getRdns();
                        owner = (String)rdn.get(5).getValue();
                } catch (Exception e) {
                        System.err.println("Could not get owner name.");
                }
                return owner;
        }

        
	public static void main(String[] args) {               
		Server fs = new Server(1343);
		fs.start();
	}

}