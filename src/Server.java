import java.util.*;
import java.io.BufferedInputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.EOFException;
import java.io.File;
import java.io.FileInputStream;
//import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.ObjectInputStream;
//import java.io.OutputStream;
import java.io.PrintWriter;
import static java.lang.Math.round;
import java.net.ServerSocket;
//import java.net.Socket;
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
    private static KeyStore ks;
    private FileTable fileTable;
	
	public Server(int port) {
//        fileTable = new FileTable();
		this.fileTable = (FileTable) readFileTable();
		if (this.fileTable == null){
			System.err.println("Error reading FileTable, creating new FileTable.ser");
			this.fileTable = new FileTable();
			this.fileTable.saveFileTable();
		}
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
	
	private Object readFileTable(){
		try{
			FileInputStream filestream = new FileInputStream("FileTable.ser");
			ObjectInputStream ois = new ObjectInputStream(filestream);
			Object obj = ois.readObject();
			ois.close();
			filestream.close();
			return obj;
		} catch (Exception e){
			return null;
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

	private boolean checkCircle(String filename, int circumference, String person){
		if (circumference == 0) return true; //return true if circumference is 0
		ArrayList<String> fileCerts = this.fileTable.getList(filename);
		if (person != null){
			boolean pavailable = false;
			for (String cert: fileCerts){
				if (cert.equals(person)){
					pavailable = true;
				}
			}
			if (pavailable == false) return false;
		}
		ArrayList<String> circle = findCircle(fileCerts, circumference, person);
		if (circle != null) return true;
		return false;
	}
	
	private ArrayList<String> findCircle(ArrayList<String> fileCerts, int circumference, String person) {
		ArrayList<ArrayList<String>> masterList = getCircles(fileCerts);
		ArrayList<ArrayList<String>> newList = new ArrayList<>();
		
		for (ArrayList<String> mlist: masterList){
//			if (mlist.size() >= circumference){
			if (mlist.size() > circumference){
				newList.add(mlist);
			}
		}
		
		for (ArrayList<String> nlist: newList){
			String first = nlist.get(0);
			String last = nlist.get(nlist.size()-1);
			if (first.equals(last)){ //Check if proper closed loop is formed
				if (person != null){ //Check if a person is required in the loop
					if (nlist.contains(person)){ //Check if this list contains that person
						boolean all = true; //Check if the list contains all the people vouching for the file
						for (String c: nlist){
							if (!(fileCerts.contains(c))){
								all = false;
							}
						}
						if (all){
							return nlist;
						}
					}
				}
				else{ //Check if list contains all people vouching for file
					boolean all = true;
					for (String c: nlist){
						if (!(fileCerts.contains(c))){
							all = false;
						}
					}
					if (all){
						return nlist;
					}
				}
			}
		}
		return null;
	}
	
	private ArrayList<ArrayList<String>> getCircles(ArrayList<String> certs){
		ArrayList<ArrayList<String>> masterList = new ArrayList<>();
		for (String cert: certs){
			String n = null;
			String t = cert;
			ArrayList<String> list = new ArrayList<>();
			list.add(t);
			while (true){
				n = getSigner(getCert(CERTSTORE + '/' + t + ".cer"));
				if (n == null) break;
				if (list.contains(n)){
					if (n.equals(cert)){
						list.add(cert);
					}
					break;
				}
				list.add(n);
				t = n;
			}
			masterList.add(list);
		}
		return masterList;
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
        String person = null;
        int circumference = 0;
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
                    this.fileTable.addFile(requestArgs[2]); //add the file to the filetable
                    sendResult(sock, requestArgs[2] + " has been added to the server");
                } else {
                    sendResult(sock, "ERROR: Unable to save file");
                }
                break;
            case "fetch": 
                circumference = dis.readInt();
                dis.read(buffer, 0, BUFFSIZE); //read the trustedname
                String trustedname = new String(buffer).split("\0")[0];
                if(!trustedname.equals("(null)")) person = trustedname; //checks whether a trustedname was actually sent          
                
                if (!this.fileTable.fileList.contains(requestArgs[1])) {
                    sendResult(sock, "ERROR: File: " + requestArgs[1] + " does not exist on the server");
                    break;
                }
            	successful = checkCircle(requestArgs[1], circumference, person);
            	if (successful) {
                    if(person != null && circumference != 0) {
                        sendResult(sock, "Circle of circumference " + circumference + " containing " + person + " exists for file " + requestArgs[1]);
                    } else sendResult(sock, "Circle of circumference " + circumference + " exists for file " + requestArgs[1]);
                    successful = sendFile(sock, DOWNLOADS + '/' + requestArgs[1]);
                } else {
                    if(person != null) {
                        sendResult(sock, "ERROR: circle of circumference " + circumference + " containing " + person + " does not exist for file " + requestArgs[1]);
                    } else {
                        sendResult(sock, "ERROR: circle of circumference " + circumference + " does not exist for file " + requestArgs[1]);
                    }
                } if(successful) {
                    sendResult(sock, requestArgs[1] + " has been sent to the client");
                } else {
                    sendResult(sock, "ERROR: Unable to send the file to the client");
                }
                break;
            case "list":  
                successful = getList();
                if(successful) {
                    successful = sendFile(sock, "filelist");
                    Files.delete(Paths.get("filelist"));
                    if(successful) {
                        sendResult(sock, "File list has been sent to the client");
                        break;
                    }                    
                } 
                sendResult(sock, "ERROR: Unable to send file list");                
                break;
            case "vouch":
                if (!this.fileTable.fileList.contains(requestArgs[1])) {
                    sendResult(sock, "ERROR: File: " + requestArgs[1] + " does not exist on the server");
                    return;
                }
                sendResult(sock, "File exists on the server");
                successful = saveFile(sock);
                if(successful) {
                    certOwner = processCert(requestArgs[2]);
                    if(certOwner == null) {
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
                } else sendResult(sock, "ERROR: Unable to save certificate.");
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
    	this.fileTable.addCertificate(fileName, certOwner);
        return true;
    }
    
    Boolean getList() {
    	String list = "";
    	ArrayList<String> fileList = this.fileTable.getFileList();
    	for (String filename: fileList){
                list += String.format("\n --------------------------------\n| Filename: %20s |"
                        + "\n --------------------------------\n| Vouched for by:%15s |", filename,"");
    		ArrayList<String> certs = this.fileTable.getList(filename);
    		for (String cert: certs){
    			list += String.format("\n| %30s |", cert);
    		}
                list += "\n --------------------------------\n";
    	}
        
        
        try(PrintWriter out = new PrintWriter("filelist")  ){
            out.println(list);
            return true;
        } catch (Exception e) {
            return false;
        }
    }
        
    void sendResult(SSLSocket clientSock, String msg) throws IOException {
        try {
            DataOutputStream dos = new DataOutputStream(clientSock.getOutputStream());     
            String message = msg + new String(new char[BUFFSIZE]);
            System.out.println(msg);
            dos.write(message.getBytes(), 0, BUFFSIZE);         
            dos.flush();  
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
            fis.close();
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