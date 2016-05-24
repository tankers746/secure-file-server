import java.util.ArrayList;
import java.util.Hashtable;

public class FileTable {

	Hashtable<String, ArrayList<String>> table = new Hashtable<String, ArrayList<String>>();
	
	public void addFile(String filename){
		table.put(filename, new ArrayList<String>());
	}
	
	public void addCertificate(String filename, String certificate){
		//Is the certificate String?
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
			return null; //Return null if filename doesn't exist in the table
		}
	}
	
}
