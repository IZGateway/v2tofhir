package gov.cdc.izgw.v2tofhir.utils;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.Reader;

import org.apache.commons.io.IOUtils;
import org.apache.commons.lang3.StringUtils;

/**
 * A filter that reads lines from a reader with continuation lines (lines ending in a backslash) collapsed
 * into a single line.
 * 
 * @author Audacious Inquiry
 */
public class ContinuationReader implements AutoCloseable {
	private final BufferedReader br;
	int lineNo = 0;
	/**
	 * Construct a continuation reader from a reader
	 * @param in	The reader
	 */
	public ContinuationReader(Reader in) {
		this.br = IOUtils.toBufferedReader(in);
	}
	/**
	 * Get the 1-based line number read
	 * @return The line number read
	 */
	public int getLine() {
		return lineNo;
	}
	
	/**
	 * @return Reads a line of input, trims it, and returns it, appending
	 * the next line and any following continuation lines terminated with 
	 * a backslash
	 * 
	 * This method allows lines to be written as:
	 * <pre>
	 * This is a line
	 * This is a continuation line \
	 *   This is more of the previous line with further continuation \
	 *   This is the last of it
	 *   This is the next line
	 * </pre>
	 * 
	 * And read as:
	 * <pre>
	 * This is a line
	 * This is a continuation line This is more of the previous line with further continuation This is the last of it
	 * This is the next line
	 * </pre>
	 * 
	 * Leading whitespace on any line are trimmed.
	 * Terminal whitespace before the continuation character (\) is not trimmed,
	 * but whitespace after it is.
	 *    
	 * @throws IOException If an error occurred while reading
	 */
	public String readLine() throws IOException {
		String line = br.readLine();
		lineNo ++;
		if (line == null) {
			return line;
		}
		line = line.trim();
		if (line.isEmpty() || !line.endsWith("\\")) {
			return line;
		}
		
		String result = StringUtils.left(line, line.length() - 1);
		line = readLine();	// Recursively call to get next line
		if (line != null) {
			return result + line;
		}
		return result;
	}
	@Override
	public void close() throws Exception {
		if (br != null) {
			br.close();
		}
	}
	
}