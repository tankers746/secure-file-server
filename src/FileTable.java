
import java.util.ArrayList;
import java.util.Hashtable;

public class FileTable {

	Hashtable<String, ArrayList<String>> table = new Hashtable<>();
	ArrayList<String> fileList = new ArrayList<>();
	
	public void addFile(String filename){
		table.put(filename,  new ArrayList<String>());
		if (!(fileList.contains(fileList))){
			fileList.add(filename);
		}
	}
	
	public void addCertificate(String filename, String certificate){
		if (table.containsKey(filename) == false){
			System.err.println("Filename not found. Can't add certificate!");
		}
		else{
			ArrayList<String> currList = (ArrayList<String>) table.get(filename);
			currList.add(certificate);
			table.put(filename, currList);
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
