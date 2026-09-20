package fr.patrimoine.api.error;

import fr.patrimoine.api.correlation.CorrelationIdFilter;
import fr.patrimoine.application.error.ResourceNotFoundException;
import fr.patrimoine.domain.error.DomainException;
import java.net.URI;
import java.util.List;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.WebRequest;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;

/**
 * Turns every failure into an RFC 7807 problem document.
 *
 * <p><b>Business rules are mapped by code, not by class.</b> {@link DomainException} carries a
 * stable {@code code()}, and this handler looks that code up in a table whose default is 422. A new
 * rule in the domain therefore arrives with a correct status and a machine-readable type without
 * anyone editing the web layer &mdash; which is the property the domain's own documentation
 * promises. The table holds only the exceptions to the default: a currency that does not match the
 * account is a malformed request, not a refused operation, so it is a 400.
 *
 * <p>Extending {@link ResponseEntityExceptionHandler} means Spring's own failures &mdash;
 * unparsable body, wrong media type, unsupported method &mdash; come back as problem documents too,
 * instead of a differently shaped error for every client to special-case.
 *
 * <p>Every document carries the request's correlation id, so the id a user quotes from an error
 * message is the id that finds the request in the logs.
 */
@RestControllerAdvice
class ApiExceptionHandler extends ResponseEntityExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(ApiExceptionHandler.class);

    /** Problem types are relative URIs: this API documents them, it does not host a website. */
    private static final String TYPE_PREFIX = "/problems/";

    // 422, under the name RFC 9110 gave it. Spring keeps UNPROCESSABLE_ENTITY as a deprecated
    // alias of the same code, and the name is what the problem document carries as its title.
    private static final HttpStatus RULE_REFUSED = HttpStatus.UNPROCESSABLE_CONTENT;
    private static final Map<String, HttpStatus> STATUS_BY_CODE =
            Map.of("currency-mismatch", HttpStatus.BAD_REQUEST);

    @ExceptionHandler(DomainException.class)
    ProblemDetail handleBusinessRule(DomainException exception) {
        HttpStatus status = STATUS_BY_CODE.getOrDefault(exception.code(), RULE_REFUSED);
        return problem(status, exception.code(), exception.getMessage());
    }

    @ExceptionHandler(ResourceNotFoundException.class)
    ProblemDetail handleNotFound(ResourceNotFoundException exception) {
        return problem(HttpStatus.NOT_FOUND, exception.code(), exception.getMessage());
    }

    /**
     * The domain throws these for arguments that are structurally wrong rather than commercially
     * refused, such as a negative deposit. That is the caller's mistake, so 400.
     */
    @ExceptionHandler(IllegalArgumentException.class)
    ProblemDetail handleIllegalArgument(IllegalArgumentException exception) {
        return problem(HttpStatus.BAD_REQUEST, "invalid-request", exception.getMessage());
    }

    /**
     * Two writers touched the same account at once and the optimistic lock caught it. Nothing is
     * wrong with the request, so it says so and invites a retry rather than blaming the caller.
     */
    @ExceptionHandler(OptimisticLockingFailureException.class)
    ProblemDetail handleConcurrentModification(OptimisticLockingFailureException exception) {
        log.warn("Concurrent modification rejected", exception);
        return problem(
                HttpStatus.CONFLICT,
                "concurrent-modification",
                "This account was modified by another request. Please retry.");
    }

    /**
     * Anything unforeseen. The detail is deliberately fixed: stack traces and driver messages
     * describe the server's internals, which belong in the log, not in a response.
     */
    @ExceptionHandler(Exception.class)
    ProblemDetail handleUnexpected(Exception exception) {
        log.error("Unhandled failure", exception);
        return problem(
                HttpStatus.INTERNAL_SERVER_ERROR,
                "internal-error",
                "The request could not be processed.");
    }

    @Override
    protected ResponseEntity<Object> handleMethodArgumentNotValid(
            MethodArgumentNotValidException exception,
            HttpHeaders headers,
            HttpStatusCode status,
            WebRequest request) {
        ProblemDetail body =
                problem(
                        HttpStatus.BAD_REQUEST,
                        "invalid-request",
                        "The request body failed validation.");
        List<Map<String, String>> errors =
                exception.getBindingResult().getFieldErrors().stream()
                        .map(
                                error ->
                                        Map.of(
                                                "field", error.getField(),
                                                "message",
                                                        String.valueOf(error.getDefaultMessage())))
                        .toList();
        body.setProperty("errors", errors);
        return ResponseEntity.badRequest().body(body);
    }

    /** Catches the problem documents built by the framework, so they carry the id as well. */
    @Override
    protected ResponseEntity<Object> handleExceptionInternal(
            Exception exception,
            Object body,
            HttpHeaders headers,
            HttpStatusCode statusCode,
            WebRequest request) {
        ResponseEntity<Object> response =
                super.handleExceptionInternal(exception, body, headers, statusCode, request);
        if (response != null && response.getBody() instanceof ProblemDetail problem) {
            addCorrelationId(problem);
        }
        return response;
    }

    private static ProblemDetail problem(HttpStatus status, String code, String detail) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(status, detail);
        problem.setType(URI.create(TYPE_PREFIX + code));
        problem.setProperty("code", code);
        addCorrelationId(problem);
        return problem;
    }

    private static void addCorrelationId(ProblemDetail problem) {
        String correlationId = CorrelationIdFilter.current();
        if (correlationId != null) {
            problem.setProperty("correlationId", correlationId);
        }
    }
}
