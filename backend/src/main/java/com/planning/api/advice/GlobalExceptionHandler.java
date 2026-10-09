package com.planning.api.advice;

import com.planning.domain.exception.IncompatibleConfigException;
import com.planning.domain.exception.IncompatibleTimeModelException;
import com.planning.domain.exception.InfeasibleProblemException;
import com.planning.domain.exception.InvalidConfigException;
import com.planning.domain.exception.NotFoundException;
import com.planning.domain.exception.PlanningException;
import com.planning.domain.exception.UnknownConstraintException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/**
 * Le code d'erreur métier remonte tel quel au client.
 *
 * <p>C'est le même que celui écrit dans {@code solve_job.error_code} : le frontend
 * et le skill {@code infeasibility-diagnoser} lisent le même vocabulaire, et le
 * message reste celui produit par le domaine — en vocabulaire métier, pas
 * technique.
 */
@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    public record ApiError(String code, String message) {
    }

    @ExceptionHandler(NotFoundException.class)
    public ResponseEntity<ApiError> notFound(NotFoundException e) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(new ApiError(e.code(), e.getMessage()));
    }

    @ExceptionHandler({InvalidConfigException.class, IncompatibleConfigException.class,
            IncompatibleTimeModelException.class, UnknownConstraintException.class,
            InfeasibleProblemException.class})
    public ResponseEntity<ApiError> badRequest(PlanningException e) {
        return ResponseEntity.badRequest().body(new ApiError(e.code(), e.getMessage()));
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<ApiError> illegalArgument(IllegalArgumentException e) {
        return ResponseEntity.badRequest().body(new ApiError("BAD_REQUEST", e.getMessage()));
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiError> unexpected(Exception e) {
        log.error("Erreur inattendue", e);
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(new ApiError("INTERNAL_ERROR", "Une erreur inattendue est survenue."));
    }

    @ExceptionHandler(org.springframework.web.servlet.resource.NoResourceFoundException.class)
    public ResponseEntity<ApiError> unknownRoute(Exception e) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND)
                .body(new ApiError("NOT_FOUND", "Cette adresse n'existe pas."));
    }

    @ExceptionHandler({org.springframework.http.converter.HttpMessageNotReadableException.class,
            org.springframework.web.method.annotation.MethodArgumentTypeMismatchException.class})
    public ResponseEntity<ApiError> malformedInput(Exception e) {
        return ResponseEntity.badRequest().body(new ApiError("BAD_REQUEST",
                "Vérifier les dates, les nombres et les valeurs du formulaire."));
    }

    @ExceptionHandler(org.springframework.web.bind.MethodArgumentNotValidException.class)
    public ResponseEntity<ApiError> invalidForm(org.springframework.web.bind.MethodArgumentNotValidException e) {
        String message = e.getBindingResult().getFieldErrors().stream()
                .map(error -> error.getDefaultMessage()).distinct().collect(java.util.stream.Collectors.joining(" "));
        return ResponseEntity.badRequest().body(new ApiError("BAD_REQUEST", message));
    }
}
