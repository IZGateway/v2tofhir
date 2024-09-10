package gov.cdc.izgw.v2tofhir.utils;

import java.io.PrintStream;
import java.math.BigInteger;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.IllegalFormatCodePointException;

import org.apache.commons.lang3.StringUtils;

/**
 * FhirIdCodec enables encoding and decoding of ASCII printable strings into the
 * allowable characters from the FHIR IdType set. The encoding is essentially
 * the same as RFC-4648, except that underscore (_) is replaced with a period (.).
 * 
 * This class uses the Java Base64 encoder and decoder to effect encoding
 * and decoding, simply replacing the _ with . after encoding, and reversing
 * that prior to decoding.
 * 
 * This encoder can handle up to 58 characters before it exceeds the length
 * of a FHIR IdType.
 * 
 * This allows arbitrary data of a limited length to be encoded into a FHIR 
 * IdType object.
 *
 */
public class FhirIdCodec {
	private FhirIdCodec() { }
	
	private static Base64.Encoder ENCODER = Base64.getUrlEncoder();
	private static Base64.Decoder DECODER = Base64.getUrlDecoder();

	/**
	 * Encode a string into the FHIR id character set.
	 * 
	 * @param value	The string to encode
	 * @return	The encoded value
	 */
	public static String encode(String value) {
		byte[] data = value.getBytes(StandardCharsets.UTF_8);
		return StringUtils.replaceChars(ENCODER.encodeToString(data), "_=", ".");
	}
	/**
	 * Decode a string into the FHIR id character set.
	 * 
	 * @param value	The string to decode
	 * @return	The decoded value
	 * @throws IllegalFormatCodePointException if the value could properly not be decoded as a Unicode String
	 */
	public static String decode(String value) {
		value = value.replace(".", "_");
		byte[] data = DECODER.decode(value);
		String result = new String(data, StandardCharsets.UTF_8);
		if (result.contains("\uFFFD")) {
			throw new IllegalFormatCodePointException(0xFFFD);
		}
		return result;
	}
	

	/**
	 * Encode an ASCII string into the FHIR id character set.
	 * @param value	The value to encode.
	 * @return	The encoded value.
	 */
	public static String encodeAscii(String value) {
		if (!StringUtils.isAsciiPrintable(value)) {
			throw new IllegalArgumentException("Value contains non ASCII-printable characters");
		} 
		
		byte[] data = value.getBytes(StandardCharsets.US_ASCII);
		data = packAsciiBytes(data);
		return StringUtils.replaceChars(ENCODER.encodeToString(data), "_=", ".");
	}
	
	/**
	 * Decode an ASCII string from the FHIR id character set.
	 * @param value	The value to decode.
	 * @return A string containing the decoded value.
	 * @throws IllegalFormatCodePointException if the decoded characters are non-ASCII
	 */
	public static String decodeAscii(String value) {
		value = value.replace(".", "_");
		byte[] data = DECODER.decode(value);
		BigInteger result = new BigInteger(data);
		StringBuilder b = new StringBuilder();
		BigInteger mult = BigInteger.valueOf(96);
		while (!result.equals(BigInteger.ZERO)) {
			BigInteger[] div = result.divideAndRemainder(mult);
			byte v = div[1].byteValue();
			result = div[0];
			if (v < 0 || v >= 96) {
				throw new IllegalFormatCodePointException(v + 32);
			}
			v += 32;
			b.append((char)v);
		}
		// Reverse the data
		for (int i = 0, j = b.length() - 1; i < j; i++, j--) {
			char left = b.charAt(i);
			b.setCharAt(i, b.charAt(j));
			b.setCharAt(j, left);
		}
		return b.toString();
	}
	
	/**
	 * Print binary data in HEX and ASCII in a human readable form
	 * @param out	The output stream to write to
	 * @param data	The data to write
	 */
	public static void dumpHex(PrintStream out, byte[] data) {
		int counter = 0;
		for (byte b: data) {
			out.printf("%02X", b);
			if (++counter % 4 == 0) out.print(" ");
		}
		counter = 0;
		out.print("  ");
		for (byte b: data) {
			char c = ((b & 0xff) < 32) ? '.' : (char)(b & 0xff);
			out.printf("%c", c);
			if (++counter % 8 == 0) out.print(" ");
		}
		out.println();
	}
	
	/**
	 * Pack ASCII printable characters into data as tightly as possible.
	 * @param data	The ASCII characters to pack.
	 * @return	The packed character array.
	 */
	private static byte[] packAsciiBytes(byte[] data) {
		BigInteger result = BigInteger.ZERO;
		BigInteger mult = BigInteger.valueOf(96);
		for (byte b: data) {
			result = result.multiply(mult);	// Multiplier = 96
			int v = (b & 0x7F) - 32;		// Now in range of 0-95
			result = result.add(BigInteger.valueOf(v));
		}
		return result.toByteArray();
	}
	
	/**
	 * Test route
	 * @param args	Strings to convert	
	 */
	public static void main(String ... args) {
		// Default values to use for testing.
		String[] values = {
			"MYEHR|234814",
			"MYEHR|234814Z",
			"MYEHR|234814ZZ",
			"MYEHR%7C234814",
			"MYEHR%7C234814Z",
			"MYEHR%7C234814ZZ",
			"~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~",
			"~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~",
			"~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~",
			"~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~",
			"~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~",
			"!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!",
			"!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!",
			"!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!",
			"!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!",
			"!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!",
			"!                                                     !",
			"!                                                      !",
			"!                                                       !",
			"!                                                        !",
			"!                                                         !",
		};
		if (args.length != 0) {
			values = args;
		}
		for (String value: values) {
			String encode = encode(value);
			String decode = decode(encode);
			System.out.printf("'%s'(%d) -> '%s'(%d) -> '%s' : %s%n", value, value.length(), encode, encode.length(), decode, value.equals(decode));
		}
	}
}
