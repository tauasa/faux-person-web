package org.tauasa.faux.web;

/** A request parameter that can't be used. Answered with 400 naming it. */
public class BadParameterException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    private final String parameter;

    /**
     * @param parameter the parameter's name
     * @param message   what it accepts, starting with the parameter's name
     */
    public BadParameterException(String parameter, String message) {
        super(message);
        this.parameter = parameter;
    }

    public String parameter() {
        return parameter;
    }
}
