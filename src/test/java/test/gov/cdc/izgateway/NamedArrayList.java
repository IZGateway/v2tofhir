package test.gov.cdc.izgateway;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Map;
import java.util.TreeMap;

/**
 * NamedArrayList is used to make automated lists test cases which
 * are intended to be extensive in what they test.  
 * 
 * They enable test inputs to be automatically generated once
 * and cached for reuse when needed again later without repeating
 * the expensive generation process.
 * 
 * There may be to many cases for for routine testing, so it also
 * offers the capability to generate a selection from the total set
 * for more frequent regression testing vs. release testing.
 */
public class NamedArrayList 
	extends ArrayList<String> 
	implements Comparable<NamedArrayList> 
{
	private static final long serialVersionUID = 1L;
	private static Map<String, NamedArrayList> cachedResults = new TreeMap<>();
	/** The name of the list */
	private final String name;
	/**
	 * Construct a named array list with a name and values
	 * @param name The name of the array
	 * @param values	The values to store
	 */
	public NamedArrayList(String name, String ...values) {
		this.name = name;
		if (cachedResults.get(name) != null) {
			throw new IllegalArgumentException("Duplicate NamedArrayList: " + name);
		}
		cachedResults.put(name, this);
		if (values.length != 0) {
			this.addAll(Arrays.asList(values));
		}
	}
	/**
	 * Construct a named array list with a singleton value that is both
	 * name and value.
	 * @param name	The singletone
	 * @return	The NamedArrayList
	 */
	public static NamedArrayList singleton(String name) {
		return l(name, name);
	}
	/**
	 * Construct a named array list with a name and values
	 * @param name	The name
	 * @param values the values
	 * @return	The named array list
	 */
	public static NamedArrayList l(String name, String ... values) {
		NamedArrayList l = cachedResults.get(name);
		if (l != null) {
			return l;
		}
		l = new NamedArrayList(name);
		l.addAll(Arrays.asList(values));
		return l;
	}
	/**
	 * Find an existing named array list
	 * @param name	The name
	 * @return	The found list
	 */
	public static NamedArrayList find(String name) {
		NamedArrayList found = cachedResults.get(name);
		if (found != null) {
			return found;
		}
		return new NamedArrayList(name);
	}
	/**
	 * Get the name of this list
	 * @return	The name of the list
	 */
	public String name() {
		return name;
	}
	@Override
	public int compareTo(NamedArrayList that) {
		if (this == that) {
			return 0;
		}
		return this.name.compareTo(that.name); 
	}
	/**
	 * Return true if the list is not empty
	 * @return true if not empty, false otherwise
	 */
	public NamedArrayList notEmpty() {
		System.out.println(name + " has " + size() + " elements.");
		TestUtils.assertNotEmpty(this);
		return this;
	}
	
	/**
	 * Truncate the list to the new size if necessary (used to save space
	 * for large cross-combinations)
	 * @param newSize	The new size
	 * @return	the NamedArrayList (which was modified in place)
	 */
	public NamedArrayList truncate(int newSize) {
		if (size() < newSize || newSize < 0) {
			return this;
		}
		System.out.printf("Truncating %s(%d) to %d%n", name(), size(), newSize);
		ArrayList<String> l = new ArrayList<>();
		// Choose every Nth item from original test cases so we get 
		// examples from each subgroup within the list instead of truncating 
		// any test case subgroup.
		int divisor = size() / newSize;
		int max = divisor * newSize;
		for (int i = 0; i < max; i += divisor) {
			l.add(this.get(i));
		}
		this.clear();
		this.addAll(l);
		return this;
	}
}