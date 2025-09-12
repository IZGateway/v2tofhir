package gov.cdc.izgw.v2tofhir.utils;

import java.lang.ref.SoftReference;
import java.lang.ref.WeakReference;

import lombok.extern.slf4j.Slf4j;

/**
 * 
 * The ErrorReporter interface provides a mechanism for reporting warning messages during processing, so
 * that different implementations can handle warnings in a manner appropriate to their context.
 * 
 * @author Audacious Inquiry
 */
public interface ErrorReporter {
	/**
	 * The default error reporter just logs the warning message using SLF4J.
	 * @author Audacious Inquiry
	 */
	@Slf4j
	static class DefaultErrorReporter {
		/** Thread local to allow different threads to have different error reporters.
		 *  If not set, the default instance is used, which just logs the warning.
		 */
		private static final ThreadLocal<WeakReference<ErrorReporter>> ERROR_REPORTER = new ThreadLocal<>();
		public static final ErrorReporter INSTANCE = new ErrorReporter() {
			@Override
			public void warn(String message, Object... args) {
				// Default is to log the warning.
				log.warn(message, args);
			}
		};
	}
	
	/**
	 * Set the error reporter for the current thread. If not set, the default reporter is used, which just logs the warning.
	 * @param reporter	The error reporter to use. If null, the default reporter is used.
	 */
	public static void set(ErrorReporter reporter) {
		// Use a weak reference to avoid memory leaks if the reporter is no longer needed.
		WeakReference<ErrorReporter> ref = new WeakReference<>(reporter);
		DefaultErrorReporter.ERROR_REPORTER.set(ref);
	}
	
	/**
	 * Get the error reporter for the current thread. If not set, the default reporter is used, which just logs the warning.
	 * @return The error reporter for the current thread.
	 */
	public static ErrorReporter get() {
		WeakReference<ErrorReporter> ref = DefaultErrorReporter.ERROR_REPORTER.get();
		if (ref != null) {
			ErrorReporter r = ref.get();
			if (r != null) {
				return r;
			}
		}
		return DefaultErrorReporter.INSTANCE;
	}
	
	/**
	 * Handle a warning message. Implementations may choose to log the message, or throw a runtime exception,
	 * to ensure that the calling code is aware of the warning.
	 * 
	 * @param message The warning message
	 * @param args The arguments. If the last argument is a Throwable, it is treated as the cause.
	 */
	default void warn(String message, Object ... args) {
		ErrorReporter r = get();
		if (r != null) {
			r.warn(message, args);
			return;
		}
		DefaultErrorReporter.INSTANCE.warn(message, args);
	}
}
