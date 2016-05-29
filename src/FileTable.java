
import java.io.FileOutputStream;
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
		saveFileTable();
	}
	
	public void addCertificate(String filename, String certificate){
		if (table.containsKey(filename) == false){
			System.err.println("Filename not found. Can't add certificate!");
		}
		else{
			ArrayList<String> currList = (ArrayList<String>) table.get(filename);
			currList.add(certificate);
			table.put(filename, currList);
			saveFileTable();
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
	
	public void saveFileTable(){
		try{
			FileOutputStream fs = new FileOutputStream("FileTable.ser");
			ObjectOutputStream os = new ObjectOutputStream(fs);
			os.writeObject(this);
			os.close();
		} catch (Exception e){
			e.printStackTrace();
		}
	}
	
}
