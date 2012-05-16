import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;

 /**
  * 1. Distributes the SNPs in one file into many files as per the user defined constant
  * 2. Puts the SNPs into columns from a list on one row
  * 
  * @author Skanda Koppula
  * skoppula@andover.edu
  * @version 2.27.2011
 **/

public class DataPartition {
	
	public static void main(String[] args) throws IOException{
		
		final String INPUTPATH = args[0];
		final int NUM_SNPS_PER_FILE = Integer.parseInt(args[1]);
		final String DIRECTORY_OF_DISTRFILES  = args[2]+"/";
		
		createDirectory(DIRECTORY_OF_DISTRFILES);
		
		BufferedReader input = new BufferedReader(new FileReader(INPUTPATH));

		int SNPCounter = 0;
		int fileNumber = 1;
		
		//Iterates over the files
		for(boolean endOfSNPList = false; endOfSNPList == false; fileNumber++){
			
			String outputfile = DIRECTORY_OF_DISTRFILES + "snp-partition-" + fileNumber + ".wr";

			//Deletes the contents of any file with the same name
			FileWriter output = new FileWriter(outputfile);
			output = new FileWriter(outputfile, true);
			
			//Iterates over each SNP in the file
			String SNP = "";
			for(SNPCounter = 0; SNPCounter < NUM_SNPS_PER_FILE; SNPCounter++){
				
				SNP = "";
				char nextChar = (char) input.read();
				
				while(nextChar!=44){
					SNP+=nextChar;
					nextChar = (char) input.read();
					if(SNP.equals("sex")) {
						endOfSNPList = true;
						break;
					}
				}
				
				if(endOfSNPList==true)
					break;
				else
					output.write(SNP + "\r\n");
				
			}
			
			output.close();
		}
	}
	
	public static void createDirectory(String directory){
		try{
			boolean success = (new File(directory)).mkdir();
			if (success) {
				System.out.println("Directory: " + directory + " created");
			}

		} catch (Exception e) {
			System.err.println("Error: " + e.getMessage());
		}
	}
}