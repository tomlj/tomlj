package org.tomlj;

@SuppressWarnings("serial")
public class JSONException extends RuntimeException {

    public JSONException(final String message) {
        super(message);
    }

    /**
     * Constructs a JSONException with an explanatory message and cause.
     * 
     * @param message Detail about the reason for the exception.
     * @param cause The cause.
     */
    public JSONException(final String message, final Throwable cause) {
        super(message, cause);
    }

    /**
     * Constructs a new JSONException with the specified cause.
     * 
     * @param cause The cause.
     */
    public JSONException(final Throwable cause) {
        super(cause.getMessage(), cause);
    }
}