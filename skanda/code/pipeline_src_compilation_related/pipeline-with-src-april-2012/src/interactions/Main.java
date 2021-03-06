package interactions;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.io.PrintStream;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.Set;

import main.Concept;
import main.Gene;
import main.SNP;
import main2.ComputePredictivePower;

/**
 * Class for generating graphs out of lists of concepts of ontologies, generated by the pipeline. The nodes of the graph
 * are the concepts with significant predictive power, while the edges of the graphs indicate concepts which share
 * relevant SNPs or genes. See {@link #main(String[])} for a more detailed description of inputs and functionality.
 * 
 * @author Kent
 */
public class Main {
	/**
	 * The location of the output directory. This is specified by the second argument given by the user; if no such
	 * argument exists, then the default is used.
	 */
	private String outputDir = DEFAULT_OUTPUT_DIR;
	/**
	 * Default output directory.
	 */
	private static final String DEFAULT_OUTPUT_DIR = "interactions";

	/**
	 * A String representing the nicotine dependence concept in the graph.
	 */
	private String centralNode;
	/**
	 * The color of the central node.
	 */
	private String centralNodeColor;

	/**
	 * An array containing the attributes of the input files.
	 */
	private InputFile[] files;

	/**
	 * Determines if graphs should be generated for all possible combinations of the ontologies. If false, then only the
	 * graph corresponding to the interactions between all ontologies is generated. This is false by default.
	 */
	private boolean writeAll = false;

	/**
	 * Mapping from SNPs to their parents.
	 */
	private Map<SNP, List<SNP>> parentsMap;

	/**
	 * Indicates whether the new method of determining interactions should be used.
	 */
	private boolean useNewMethod;
//	/**
//	 * The number of the column containing the relevant items of each concept in the file.
//	 */
//	private int startIndex;
	/**
	 * Indicates whether the results were derived from SNP or gene data.
	 */
	private boolean isSNPData;

	/**
	 * Object used to compute significant interactions under the new method.
	 */
	private static ComputePredictivePower cpp;

	/**
	 * Properties of the node and edge lists. This is the first row of the table fed to yEd, and allows identification
	 * of the properties on the graph labels.
	 */
	private enum Property {
		/** The source node of an edge. */
		SOURCE("Source"),
		/** The target node of an edge. */
		TARGET("Target"),
		/**
		 * Whether an edge is bidirectional (i.e. has arrowheads on both sides, or only has one from source to target).
		 */
		IS_BIDIRECTIONAL("Bidirectional?"),
		/**
		 * The color indicating the type of interaction. There are two possibilities - either the concepts share the
		 * same gene ({@link #SAME_GENE}) or the concepts contain SNPs which are part of a parent-child relationship (
		 * {@link #PARENT_CHILD}).
		 */
		TYPE("Type color"),
		/**
		 * The name of the central node, used to determine the dataset this edge belongs to.
		 */
		DATA_NAME_EDGE("Data name (edge)"),
		/** The name of a node (i.e. the concept name). */
		NAME("Concept"),
		/** The name of the ontology associated with the node. */
		ONTOLOGY("Ontology"),
		/**
		 * The color of a node. This is determined by the ontology of the concept, i.e. the file it is derived from.
		 */
		COLOR("Color"),
		/**
		 * The name of the central node, used to determine the dataset this node belongs to.
		 */
		DATA_NAME_NODE("Data name (node)"),
		/** Dummy column 1, needed for yEd to work properly. */
		SHARED("Shared terms"),
		/** Dummy column 2, needed for yEd to work properly. */
		PARENT_CHILD("Parent-child SNPs");

		private enum TypeColor {
			PHENOTYPE, DEFAULT, BOTH, PARENT_CHILD;
			final static Map<TypeColor, String> nameToColor = new HashMap<TypeColor, String>();

			public static void addColor(TypeColor type, String colorString) {
				nameToColor.put(type, colorString);
			}

			public static String getColor(TypeColor type) {
				return nameToColor.get(type);
			}
		}

		/** The name of the property. */
		private final String name;

		/**
		 * Constructs a new property. This constructor should only be used by this Enum.
		 * 
		 * @param name
		 *            the name of the property.
		 */
		private Property(String name) {
			this.name = name;
		}

		/**
		 * Returns the name of the property.
		 * 
		 * @return the name of the property.
		 */
		public String toString() {
			return name;
		}

		/**
		 * Returns all the property strings, in order.
		 * 
		 * @return an array of the property strings, in order.
		 */
		public static String[] propertyStrings() {
			String[] result = new String[Property.values().length];
			for (int i = 0; i < Property.values().length; i++) {
				result[i] = Property.values()[i].toString();
			}
			return result;
		}
	}

	/**
	 * Map of tables associated with each single concept (i.e. the interactions within each concept only).
	 */
	private Map<InputFile, Table> singleTables;
	/**
	 * Map of tables associated with each pair of concepts (i.e. the interactions between those concepts only).
	 */
	private Map<UOPair<InputFile>, Table> pairTables;

	/**
	 * Constructs a parser object for creating a graph.
	 * 
	 * @param fileName
	 *            A text file with each line tab-delimited to three columns. The first column gives the file containing
	 *            results from a particular ontology, the second column gives the name of the ontology, and the third
	 *            column gives the colour.
	 */
	public Main(Properties p) throws IOException {
		String fileName = p.getProperty("inputTable");
		String parentsFile = p.getProperty("parentsTable", null);
		centralNode = p.getProperty("centralNode", "Nicotine Dependence");
		centralNodeColor = p.getProperty("centralNodeColor", "#00ffffff");
		writeAll = p.getProperty("generateAllGraphs", "n").equals("y");
		outputDir = p.getProperty("outputDir", DEFAULT_OUTPUT_DIR);
		useNewMethod = !p.getProperty("useNewMethod", "n").equals("n");
		isSNPData = p.getProperty("isSNPData", "y").equals("y");

		String defaultColor = p.getProperty("defaultColor", "#0000ffcc");
		Property.TypeColor.addColor(Property.TypeColor.DEFAULT, defaultColor);
		Property.TypeColor.addColor(Property.TypeColor.PHENOTYPE, p.getProperty("phenotypeColor", defaultColor));
		Property.TypeColor.addColor(Property.TypeColor.BOTH, p.getProperty("bothColor", "#00ff00ff"));
		Property.TypeColor.addColor(Property.TypeColor.PARENT_CHILD, p.getProperty("parentchildColor", "#ff0000ff"));

		if (useNewMethod) {
			// System.out.println("- Extracting gene symbols ...");
			// NamedSet<Gene> genes = new ExtractGeneSymbols(p).run();
			cpp = new ComputePredictivePower(p);
		}

		parentsMap = new HashMap<SNP, List<SNP>>();

		if (parentsFile != null) {
			BufferedReader input = new BufferedReader(new FileReader(parentsFile));
			String line = null;
			while ((line = input.readLine()) != null) {
				String[] components = line.split("\\s");
				SNP snp = new SNP(components[0]);
				if (!parentsMap.containsKey(snp)) {
					parentsMap.put(snp, new LinkedList<SNP>());
				}
				parentsMap.get(snp).add(new SNP(components[1]));
			}
			input.close();
		}

		BufferedReader input = new BufferedReader(new FileReader(fileName));

		// read in location and attributes of input files
		ArrayList<InputFile> fileList = new ArrayList<InputFile>();
		String line = null;
		while ((line = input.readLine()) != null) {
			String[] components = line.split("\t");
			fileList.add(new InputFile(components[0], components[1], components[2]));
		}
		input.close();
		files = fileList.toArray(new InputFile[fileList.size()]);

		// create output directory if it doesn't exist
		new File(outputDir).mkdir();

		// parse associations between files
		parse();
	}

	/**
	 * Sets whether graphs should be generated for all possible combinations of ontologies.
	 * 
	 * @param writeAll
	 *            <CODE>true</CODE> if all possible graphs are desired; <CODE>false</CODE> if only the graph with all
	 *            ontologies should be generated.
	 */
	public void generateAllGraphs(boolean writeAll) {
		this.writeAll = writeAll;
	}

	/**
	 * Parses associations between concepts within and between ontologies.
	 * 
	 * @throws IOException
	 *             if there is an error reading any of the files.
	 */
	private void parse() throws IOException {
		singleTables = new HashMap<InputFile, Table>();
		pairTables = new HashMap<UOPair<InputFile>, Table>();

		for (int i = 0; i < files.length; i++) {
			findAssociationsWithinFile(files[i]);
			for (int j = i + 1; j < files.length; j++) {
				findAssociationsBetweenFiles(files[i], files[j]);
			}
		}
	}

	/**
	 * Writes the output graphs. Every combination of the input files is considered. For example, if three input files
	 * containing GO, KEGG, miRNA concepts were used and {@link #writeAll} is set to <CODE>true</CODE>, then the output
	 * files are GO.csv, KEGG.csv, miRNA.csv, GO+KEGG.csv, GO+miRNA.csv, KEGG+miRNA.csv, and GO+KEGG+miRNA.csv. On the
	 * other hand, if {@link #writeAll} is false, then only GO+KEGG+miRNA.csv is produced.
	 * 
	 * The output directory is specified by the user, or if not set, has a default value given by
	 * {@link #DEFAULT_OUTPUT_DIR}.
	 * 
	 * @throws IOException
	 *             if there is an error writing to file.
	 */
	public void output() throws IOException {
		process(new LinkedList<Integer>(), 0);
	}

	/**
	 * Writes the output graphs, as specified by {@link #output()}. This method uses recursion to enumerate all
	 * possibilities. Note that if {@link #writeAll} is false, then only one graph is produced (with all ontologies).
	 * 
	 * @param list
	 *            the list of indices of the ontologies, to be included in the graph.
	 * @param nextFileIndex
	 *            the next index to be considered.
	 * @throws IOException
	 *             if there is an error writing to file.
	 */
	private void process(List<Integer> list, int nextFileIndex) throws IOException {
		if (nextFileIndex >= files.length) {
			// reached end of recursion
			// check that list is nonempty
			if (list.size() > 0) {
				Integer[] indexArray = list.toArray(new Integer[list.size()]);
				Table table = new Table(Property.propertyStrings());
				String outputFile = outputDir + "\\";

				// add node entry for nicotine dependence
				addNode(centralNode, "N/A", centralNodeColor, table);

				// add entries associated with single ontology
				for (int i = 0; i < indexArray.length; ++i) {
					Table nextTable = singleTables.get(files[indexArray[i]]);
					table.append(nextTable);

					// add ontology name to name of output file
					outputFile += files[indexArray[i]].name;
					if (i + 1 < indexArray.length) {
						outputFile += "_";
					}
				}
				outputFile += ".csv";

				// add entries associated with pairs of ontologies
				for (int i = 1; i < indexArray.length; ++i) {
					for (int j = 0; j < i; ++j) {
						table.append(pairTables.get(new UOPair<InputFile>(files[indexArray[i]], files[indexArray[j]])));
					}
				}

				// write table containing node and edge lists to file
				BufferedWriter writer = new BufferedWriter(new FileWriter(outputFile));
				writer.write(table.toString());
				writer.close();
			}
		} else {
			if (writeAll) {
				// consider graphs where some ontologies are missing
				process(list, nextFileIndex + 1);
			}

			List<Integer> list2 = new LinkedList<Integer>();
			list2.addAll(list);
			list2.add(nextFileIndex);
			process(list2, nextFileIndex + 1);
		}
	}

	/**
	 * Add the specified edge to the given table.
	 * 
	 * @param source
	 *            the source node of the edge.
	 * @param target
	 *            the target node of the edge.
	 * @param isBidirectional
	 *            whether the edge is a bidirectional arrow.
	 * @param table
	 *            the table to be added to.
	 */
	private void addEdge(String source, String target, boolean isBidirectional, Property.TypeColor color, Table table,
			String shared, String parentChild) {
		if (!Property.TypeColor.getColor(color).equals("none")) {
			table.addEntry(Property.SOURCE.toString(), source);
			table.addEntry(Property.TARGET.toString(), target);
			table.addEntry(Property.IS_BIDIRECTIONAL.toString(), isBidirectional ? "y" : "n");
			table.addEntry(Property.DATA_NAME_EDGE.toString(), centralNode);
			table.addEntry(Property.TYPE.toString(), Property.TypeColor.getColor(color));
			table.addEntry(Property.SHARED.toString(), shared);
			table.addEntry(Property.PARENT_CHILD.toString(), parentChild);
		}
	}

	private void addEdge(String source, String target, boolean isBidirectional, Property.TypeColor color, Table table) {
		addEdge(source, target, isBidirectional, color, table, "", "");
	}

	/**
	 * Determines the concepts which are associated between two ontologies, as specified in the given input files. Two
	 * concepts are associated if they share an SNP in the reduced table list, or a gene which contains SNPs in the
	 * reduced table list.
	 * 
	 * @param file1
	 *            the file containing the relevant concepts of the first ontology.
	 * @param file2
	 *            the file containing the relevant concepts of the second ontology.
	 * @throws IOException
	 *             if there is an error reading from the files.
	 */
	private void findAssociationsBetweenFiles(InputFile file1, InputFile file2) throws IOException {
		Table table = new Table(Property.propertyStrings());

		List<Concept> concepts1 = extractConcepts(file1.path);
		List<Concept> concepts2 = extractConcepts(file2.path);

		for (Concept c1 : concepts1) {
			for (Concept c2 : concepts2) {
				checkInteraction(table, c1, c2);
			}
		}

		pairTables.put(new UOPair<InputFile>(file1, file2), table);
	}

	private void checkInteraction(Table table, Concept c1, Concept c2) {
		if (c1.equals(c2)) {
			return;
		}

		if (useNewMethod) {
			newMethod(table, c1, c2);
		} else {
			oldMethod(table, c1, c2);
		}
	}

	private void newMethod(Table table, Concept c1, Concept c2) {
		// boolean shareGeneOrSNP = shareGene(c1, c2) || shareSNP(c1, c2);
		boolean parentChild = shareParentChildSNP2(c1, c2);

		Collection<SNP> snps1 = c1.getSNPs();
		Collection<SNP> snps2 = c2.getSNPs();
		List<String> features = new ArrayList<String>();

		if (isSNPData) {
			for (SNP snp : snps1) {
				if (snps2.contains(snp)) {
					features.add(snp.toString());
				}
			}
		} else {
			for (Gene gene : c1.getGenes()) {
				if (c2.getGenes().contains(gene)) {
					features.add(gene.toString());
				}
			}
		}
		
		boolean snpsSignificant = testSignificance(features);

		Property.TypeColor color = Property.TypeColor.BOTH;
		String shared = "";
		String parentChildStr = "";
		if (parentChild && !snpsSignificant) {
			color = Property.TypeColor.PARENT_CHILD;
		} else if (!parentChild && snpsSignificant) {
			color = Property.TypeColor.DEFAULT;
		}
		if (parentChild || snpsSignificant) {
			addEdge(c1.toString(), c2.toString(), true, color, table, shared, parentChildStr);
			// System.out.println(color.name());
		}
	}

	private void oldMethod(Table table, Concept c1, Concept c2) {
		if (shareGene(c1, c2) || shareSNP(c1, c2)) {
			if (shareParentChildSNP(c1, c2)) {
				addEdge(c1.toString(), c2.toString(), true, Property.TypeColor.BOTH, table);
				// System.out.println("wtf");
			}
			addEdge(c1.toString(), c2.toString(), true, Property.TypeColor.DEFAULT, table);
		} else if (shareParentChildSNP(c1, c2)) {
			// System.out.println("wtf");
			addEdge(c1.toString(), c2.toString(), true, Property.TypeColor.PARENT_CHILD, table);
		}
	}

	private boolean shareParentChildSNP(Concept c1, Concept c2) {
		for (SNP snp1 : c1.getSNPs()) {
			for (SNP snp2 : c2.getSNPs()) {
				if (parentsMap.containsKey(snp1) && parentsMap.get(snp1).contains(snp2)) {
					return true;
				} else if (parentsMap.containsKey(snp2) && parentsMap.get(snp2).contains(snp1)) {
					return true;
				}
			}
		}
		return false;
	}
	
	private boolean shareParentChildSNP2(Concept c1, Concept c2) {
		Set<String> features = new HashSet<String>();
		for (SNP snp1 : c1.getSNPs()) {
			for (SNP snp2 : c2.getSNPs()) {
				if (parentsMap.containsKey(snp1) && parentsMap.get(snp1).contains(snp2)) {
					features.add(snp1.toString());
					features.add(snp2.toString());
				} else if (parentsMap.containsKey(snp2) && parentsMap.get(snp2).contains(snp1)) {
					features.add(snp1.toString());
					features.add(snp2.toString());
				}
			}
		}
		return testSignificance(features);
	}

	private boolean testSignificance(Collection<String> features) {
		if (features.size() != 0) {
//			 System.out.println("Computing correlation between " + snp1 + " and " + snp2);
//			 System.out.println("\tSNPs: " + Utilities.colToString(features));
			try {
				double pValue = cpp.findPValue(features).get(0);
//				 System.out.println("\tp-value: " + pValue);
				if (pValue < 0.05) {
					return true;
					// addEdge(c1.toString(), c2.toString(), true, Property.TypeColor.DEFAULT, table);
				}
			} catch (Exception e) {
				System.err.println("test for significance failed");
				return false;
			}
		}
		return false;
	}

	/**
	 * Determines the concepts which are associated within one ontology. See the description for
	 * {@link #findAssociationsBetweenFiles(InputFile, InputFile)} for further details. In particular, a unidirectional
	 * association is added between nicotine dependence and every other concept.
	 * 
	 * @param file
	 *            the file containing the ontology.
	 * @throws IOException
	 *             if there is an error reading from the files.
	 */
	private void findAssociationsWithinFile(InputFile file) throws IOException {
		Table table = new Table(Property.propertyStrings());

		List<Concept> concepts = extractConcepts(file.path);

		// add node data and association with central node
		for (Concept concept : concepts) {
			addNode(concept.toString(), file.name, file.color, table);
			addEdge(centralNode, concept.toString(), false, Property.TypeColor.PHENOTYPE, table);
		}

		Concept[] conceptArray = concepts.toArray(new Concept[concepts.size()]);
		for (int i = 0; i < conceptArray.length; ++i) {
			for (int j = 0; j < i; ++j) {
				Concept c1 = conceptArray[i];
				Concept c2 = conceptArray[j];

				checkInteraction(table, c1, c2);
			}
		}

		singleTables.put(file, table);
	}

	/**
	 * Adds a node to the table. The nodes represent the concepts of an ontology. Nodes are color-coded based on the
	 * ontology they belong to.
	 * 
	 * @param concept
	 *            the concept to be added.
	 * @param ontology
	 *            the name of the ontology of the concept.
	 * @param color
	 *            the color of the node.
	 * @param table
	 *            the table to whic this node is added.
	 */
	private void addNode(String concept, String ontology, String color, Table table) {
		table.addEntry(Property.NAME.toString(), concept);
		table.addEntry(Property.ONTOLOGY.toString(), ontology);
		table.addEntry(Property.COLOR.toString(), color);
		table.addEntry(Property.DATA_NAME_NODE.toString(), centralNode);
	}

	/**
	 * Checks whether two concepts share a gene, which contains an SNP in the reduced SNP list.
	 * 
	 * @param c1
	 *            the first concept.
	 * @param c2
	 *            the second concept.
	 * @return <CODE>true</CODE> if the concepts share a gene.
	 */
	private static boolean shareGene(Concept c1, Concept c2) {
		for (Gene g1 : c1.getGenes()) {
			for (Gene g2 : c2.getGenes()) {
				if (g1.equals(g2)) {
					// System.out.println(c1 + ", " + c2 + ", " + g1);
					return true;
				}
			}
		}
		return false;
	}

	/**
	 * Checks whether two concepts share an SNP.
	 * 
	 * @param c1
	 *            the first concept.
	 * @param c2
	 *            the second concept.
	 * @return <CODE>true</CODE> if the concepts share an SNP.
	 */
	private static boolean shareSNP(Concept c1, Concept c2) {
		for (SNP snp1 : c1.getSNPs()) {
			for (SNP snp2 : c2.getSNPs()) {
				if (snp1.equals(snp2)) {
					return true;
				}
			}
		}
		return false;
	}

	/**
	 * Extracts the concepts from an ontology, given a file containing the results from running the pipeline. In
	 * particular, the file is assumed to be tab-delimited, with the first column corresponding the concept name, the
	 * fifth column containing the genes associated with a concept, and the sixth column containing the SNPs associated
	 * with a concept. The list of genes and SNPs are comma delimited. The first row is assumed to contain category
	 * labels (properties), and is thus not included in the concept list.
	 * 
	 * 
	 * @param fileName
	 *            the name of the file.
	 * @return a List of Concept objects encoding the information specified above.
	 * @throws IOException
	 *             if there is an error reading the file.
	 */
	private List<Concept> extractConcepts(String fileName) throws IOException {
		List<Concept> concepts = new LinkedList<Concept>();
		String regex = "\t";
		int startIndex = isSNPData ? 4 : 3;

		BufferedReader input = new BufferedReader(new FileReader(fileName));
		String line = input.readLine();// null;
		while ((line = input.readLine()) != null) {
			if (line.trim().equals("")) {
				continue;
			}

			String[] components = line.split(regex);
			Concept concept = new Concept(components[0]);

			String[] geneStrs = components[startIndex].split("\"|,");
			for (String geneStr : geneStrs) {
				// ignore empty Strings
				if (!geneStr.trim().equals("")) {
					concept.addGene(new Gene(geneStr));
				}
			}
			// System.out.println(concept.getGenes());

			if (components.length > startIndex + 1) {
				String[] snpStrs = components[startIndex + 1].split("\"|,");
				for (String snpStr : snpStrs) {
					// ignore empty Strings
					if (!snpStr.trim().equals("")) {
						concept.addSNP(new SNP(snpStr));
					}
				}
			}
			// System.out.println(concept.getSNPs());
			concepts.add(concept);
		}
		input.close();

		return concepts;
	}

	/**
	 * A simple class containing the attributes of a file, representing the relevant concepts from an ontology.
	 */
	class InputFile {
		/**
		 * The location of the file.
		 */
		final String path;
		/**
		 * The name of the ontology.
		 */
		final String name;
		/**
		 * The color code of the ontology. This can either be in hexadecimal RGB (e.g. #ffffff) or RGBA (e.g. #ffffffff)
		 * format.
		 */
		final String color;

		/**
		 * Initializes an InputFile object with the given attributes.
		 * 
		 * @param path
		 *            the location of the file.
		 * @param name
		 *            the name of the ontology.
		 * @param color
		 *            the color code of the ontology.
		 */
		InputFile(String path, String name, String color) {
			this.path = path;
			this.name = name;
			this.color = color;
		}

		/**
		 * Returns the hashcode of this object. This method was automatically generated by Eclipse, based off the
		 * {@link Main.InputFile#path} variable only.
		 * 
		 * @return the hashcode of this object.
		 */
		@Override
		public int hashCode() {
			final int prime = 31;
			int result = 1;
			result = prime * result + getOuterType().hashCode();
			result = prime * result + ((path == null) ? 0 : path.hashCode());
			return result;
		}

		/**
		 * Returns whether the given object is equivalent to this object. This method was automatically generated by
		 * Eclipse, based off the {@link Main.InputFile#path} variable only.
		 * 
		 * @param obj
		 *            the object to be compared to this one.
		 * @return <CODE>true</CODE> iff the other object is an InputFile object with the same path variable.
		 */
		@Override
		public boolean equals(Object obj) {
			if (this == obj) {
				return true;
			}
			if (obj == null) {
				return false;
			}
			if (!(obj instanceof InputFile)) {
				return false;
			}
			InputFile other = (InputFile) obj;
			if (!getOuterType().equals(other.getOuterType())) {
				return false;
			}
			if (path == null) {
				if (other.path != null) {
					return false;
				}
			} else if (!path.equals(other.path)) {
				return false;
			}
			return true;
		}

		/**
		 * Returns the outer Parser object which this InputFile object is located in.
		 * 
		 * @return the outer Parser object.
		 */
		private Main getOuterType() {
			return Main.this;
		}

		/**
		 * Returns a String representation of this object.
		 * 
		 * @return a String representation of this object.
		 */
		public String toString() {
			return name + "~" + path + "~" + color;
		}
	}

	/**
	 * Main method for running the program. The first argument should be the location of a tab-delimited file, which
	 * contains three columns: the first column is the paths of the files containing the concepts of each ontology, the
	 * second column is the names of the ontologies, and the third column is the color code of the ontologies (see also
	 * {@link Main.InputFile#color}). A sample input file is as below: <br />
	 * 
	 * <PRE>
	 * Table_GO_Gene2.txt	GO	#ccffffff
	 * KEGG_results.txt	KEGG	#ffff99ff
	 * microRNA_results.txt	miRNA	#00ff00ff
	 * </PRE>
	 * 
	 * <br />
	 * The second argument (either "-all" or "-single") indicates whether graphs should be generated for all possible
	 * combinations of ontologies; a value of "-all" will make this the case. Otherwise, the default will occur, i.e.
	 * only a graph with all ontologies will be generated.
	 * 
	 * The third argument is the directory in which the output graphs should be placed. If this argument is missing,
	 * then the default output location for Java is used.
	 * 
	 * @param args
	 *            the command-line arguments, as described above.
	 * @throws IOException
	 *             if there is an error reading or writing to files.
	 */
	public static void main(String[] args) throws IOException {
		// PrintStream sysOut = new PrintStream("interactions_log.txt");
		// System.setOut(sysOut);
		// System.out.println("Starting...");

		PrintStream sysErr = new PrintStream("interactions_error_log.txt");
		System.setErr(sysErr);
		run(args[0]);
	}

	public static void run(String propertiesFile) throws IOException {
		Properties props = new Properties();
		FileInputStream in = new FileInputStream(propertiesFile);
		props.load(in);
		in.close();

		Main parser = new Main(props);
		// Main parser = new Main(inputTableFile, parentsFile, useNewMethod);
		// parser.generateAllGraphs(generateAllGraphs);
		// parser.setOutputDir(outputDir);

		parser.output();
		System.out.println("Done!");
	}

	private void setOutputDir(String outputDir) {
		this.outputDir = outputDir;
	}
}
