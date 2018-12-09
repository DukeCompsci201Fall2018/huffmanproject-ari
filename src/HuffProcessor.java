import java.util.*;

/**
 * Although this class has a history of several years,
 * it is starting from a blank-slate, new and clean implementation
 * as of Fall 2018.
 * <P>
 * Changes include relying solely on a tree for header information
 * and including debug and bits read/written information
 * 
 * @author Owen Astrachan
 * @author Andres Montoya-Aristizabal
 * @author Luke Evans
 */

public class HuffProcessor {

	public static final int BITS_PER_WORD = 8;
	public static final int BITS_PER_INT = 32;
	public static final int ALPH_SIZE = (1 << BITS_PER_WORD); 
	public static final int PSEUDO_EOF = ALPH_SIZE;
	public static final int HUFF_NUMBER = 0xface8200;
	public static final int HUFF_TREE  = HUFF_NUMBER | 1;

	private final int myDebugLevel;
	
	public static final int DEBUG_HIGH = 4;
	public static final int DEBUG_LOW = 1;
	
	public HuffProcessor() {
		this(0);
	}
	
	public HuffProcessor(int debug) {
		myDebugLevel = debug;
	}

	/**
	 * Compresses a file. Process must be reversible and loss-less.
	 *
	 * @param in
	 *            Buffered bit stream of the file to be compressed.
	 * @param out
	 *            Buffered bit stream writing to the output file.
	 */
	public void compress(BitInputStream in, BitOutputStream out){
		
		int[] counts = readForCounts(in);
		HuffNode root = makeTreeFromCounts(counts);
		String[] codings = makeCodingsFromTree(root);
		
		out.writeBits(BITS_PER_INT, HUFF_TREE);
		writeTree(root,out);
		
		in.reset();
		writeCompressedBits(codings,in,out);
		out.close();
		
	}

	private int[] readForCounts(BitInputStream input) {
		int[] bet = new int[ALPH_SIZE + 1];
		while (true) {
			int count = input.readBits(BITS_PER_WORD);
			if (count == -1) {
				break;
			}
			bet[count]++;
		}
		bet[PSEUDO_EOF] = 1;
		return bet;
	}
	
	private HuffNode makeTreeFromCounts(int[] amount) {
		PriorityQueue<HuffNode> q = new PriorityQueue<>();
		for(int index = 0; index < amount.length; index++) { //
			if (amount[index] > 0)
				q.add(new HuffNode(index,amount[index],null,null));
		}

		while (q.size() > 1) {
		    HuffNode left = q.remove();
		    HuffNode right = q.remove();
		    HuffNode t = new HuffNode(0,left.myWeight + right.myWeight, left, right); //
		    q.add(t);
		}
		HuffNode root = q.remove(); 
		return root;
	}
	
	private String[] makeCodingsFromTree(HuffNode huff) {
		String[] encodings = new String[ALPH_SIZE + 1];
	    help(huff,"",encodings);

		return encodings;
		
	}
	
	private void help(HuffNode root, String path, String[] code) {
		if (root.myLeft == null && root.myRight == null) {
	        code[root.myValue] = path;
	        return;
	   }
		help(root.myLeft, path+"0",  code); //Possibly switch where you place the number
		help(root.myRight, path+"1",  code);
	}
	
	private void writeTree(HuffNode hn, BitOutputStream output) {
		if (hn.myLeft == null && hn.myRight ==null) {
			output.writeBits(1, 1);
	        output.writeBits(BITS_PER_WORD +1, hn.myValue);	//DOUBLE CHECK THIS CALL
	        return;
		}
		output.writeBits(1, 0); //DOUBLE CHECK THIS CALL
		writeTree(hn.myLeft, output);
		writeTree(hn.myRight, output);
	}

	private void writeCompressedBits(String[] encoding, BitInputStream input, BitOutputStream output) {
		int bit = input.readBits(BITS_PER_WORD);
		while(bit != -1) {
			String code = encoding[bit];
			output.writeBits(code.length(), Integer.parseInt(code,2));
			bit = input.readBits(BITS_PER_WORD);
		}
		String end = encoding[PSEUDO_EOF];
		output.writeBits(end.length(), Integer.parseInt(end,2));
	}
		

	/**
	 * Decompresses a file. Output file must be identical bit-by-bit to the
	 * original.
	 *
	 * @param in
	 *            Buffered bit stream of the file to be decompressed.
	 * @param out
	 *            Buffered bit stream writing to the output file.
	 */
	public void decompress(BitInputStream in, BitOutputStream out){
		int bits = in.readBits(BITS_PER_INT);
		if (bits != HUFF_TREE) {
			throw new HuffException("illegal header starts with " +bits);
		}
		HuffNode root = readTreeHeader(in);
		readCompressBits(root,in,out);
		out.close();
	}
	private HuffNode readTreeHeader(BitInputStream in) {
		int bit = in.readBits(1);
		if (bit == -1) {
			throw new HuffException("illegal header starts with " +bit);
		}
		if (bit == 0) {
		    HuffNode left = readTreeHeader(in);
		    HuffNode right = readTreeHeader(in);
		    return new HuffNode(0,0,left,right);
		}
		else {
		    int value = in.readBits(BITS_PER_WORD + 1);
		    return new HuffNode(value,0,null,null);
		}

	}
	
	private void readCompressBits(HuffNode root, BitInputStream in, BitOutputStream out) {
		HuffNode current = root; 
		   while (true) {
		       int bits = in.readBits(1);
		       if (bits == -1) {
		           throw new HuffException("bad input, no PSEUDO_EOF");
		       }
		       else { 
		           if (bits == 0) current = current.myLeft;
		           else current = current.myRight;
		           if (current.myLeft == null && current.myRight == null) {
		               if (current.myValue == PSEUDO_EOF) 
		                   break;   // out of loop
		               else {
		                   out.writeBits(BITS_PER_WORD, current.myValue);
		                   current = root; // start back after leaf
		               }
		           }
		       }
		   }
	}
}