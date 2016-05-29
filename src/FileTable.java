import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.Serializable;
import java.util.ArrayList;
import java.util.Hashtable;

public class FileTable implements Serializable{

	private static final long serialVersionUID = 1L;
	Hashtable<String, ArrayList<String>> table;
	ArrayList<String> fileList;
	
	public FileTable() {
        table = new Hashtable<>();
        fileList = new ArrayList<>();
    }
        
    public void addFile(String filename){
    	table.put(filename,  new ArrayList<String>());
		if (!(fileList.contains(filename))){
			fileList.add(filename);
		}
                saveHashTable();
                saveArrayList();                
	}
	
    public void saveHashTable(){
    	try{
    		FileOutputStream fos = new FileOutputStream("Hashtable.ser");
    		ObjectOutputStream oos = new ObjectOutputStream(fos);
    		oos.writeObject(this.table);
    		oos.close();
    		fos.close();
    	} catch(Exception e){
    		System.err.println("Error saving hash table");
    	}
    }
    
    public void saveArrayList(){
    	try{
    		FileOutputStream fos = new FileOutputStream("FileList.ser");
    		ObjectOutputStream oos = new ObjectOutputStream(fos);
    		oos.writeObject(this.fileList);
    		oos.close();
    		fos.close();
    	} catch(Exception e){
    		System.err.println("Error saving file list");
    	}
    }
    
    public Object getHashTable(){
    	Object obj = null;
    	try{
    		FileInputStream fis = new FileInputStream("Hashtable.ser");
    		ObjectInputStream ois = new ObjectInputStream(fis);
    		obj = ois.readObject();
    		ois.close();
    		fis.close();
    	} catch(Exception e){
    		System.err.println("Error reading hash table");
    	}
    	return obj;
    }
    
    public Object getArrayList(){
    	Object obj = null;
    	try{
    		FileInputStream fis = new FileInputStream("FileList.ser");
    		ObjectInputStream ois = new ObjectInputStream(fis);
    		obj = ois.readObject();
    		ois.close();
    		fis.close();
    	} catch(Exception e){
    		System.err.println("Error reading file list");
    	}
    	return obj;
    }
    
	public void addCertificate(String filename, String certificate){
		if (table.containsKey(filename) == false){
			System.err.println("Filename not found. Can't add certificate!");
		}
                else {
			ArrayList<String> currList = (ArrayList<String>) table.get(filename);
                        if(!currList.contains(certificate)) {
			currList.add(certificate);
			table.put(filename, currList);
                        saveHashTable();
                        saveArrayList();
                        }
		}
	}
	
	public ArrayList<String> getList(String filename){
		if (table.containsKey(filename)){
			return (ArrayList<String>) table.get(filename);
		}
		else{
			return null; //return null if filename doesn't exist in the table
		}
	}
	
	public ArrayList<String> getFileList(){
		return this.fileList;
	}
	
}