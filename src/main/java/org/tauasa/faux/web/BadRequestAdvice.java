package org.tauasa.faux.web;

import java.util.Map;

import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.servlet.HandlerMapping;
import org.springframework.web.servlet.ModelAndView;

import jakarta.servlet.http.HttpServletRequest;

/**
 * Answers a {@link BadParameterException} with 400 naming the parameter: as
 * the page with the message, for a browser asking for HTML, and as one line
 * of plain text for anything else, such as cURL asking for CSV.
 */
@ControllerAdvice
public class BadRequestAdvice {

    private final FauxWebProperties properties;

    public BadRequestAdvice(FauxWebProperties properties) {
        this.properties = properties;
    }

    @ExceptionHandler(BadParameterException.class)
    public Object badParameter(BadParameterException e, HttpServletRequest request) {
        String format = request.getParameter("format");
        boolean html = format == null || format.isBlank() || format.strip().equalsIgnoreCase("html");
        Kind kind = kindOf(request);
        if (html && kind != null) {
            ModelAndView page = new ModelAndView("rows",
                    Map.of("view", PageView.error(kind, properties, e.getMessage())));
            page.setStatus(HttpStatus.BAD_REQUEST);
            return page;
        }
        return ResponseEntity.badRequest().contentType(MediaType.TEXT_PLAIN)
                .body("400 Bad Request: " + e.getMessage() + "\n");
    }

    /** Every work permit was taken: try again shortly. Plain text, whatever was asked for. */
    @ExceptionHandler(RowStore.BusyException.class)
    public ResponseEntity<String> busy() {
        return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE).header("Retry-After", "2")
                .contentType(MediaType.TEXT_PLAIN).body("503 Service Unavailable: busy, try again shortly\n");
    }

    private static Kind kindOf(HttpServletRequest request) {
        Object vars = request.getAttribute(HandlerMapping.URI_TEMPLATE_VARIABLES_ATTRIBUTE);
        if (vars instanceof Map<?, ?> map && map.get("kind") instanceof String path) {
            return Kind.forPath(path).orElse(null);
        }
        return null;
    }
}
