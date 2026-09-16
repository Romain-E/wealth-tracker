package fr.patrimoine.application.error;

/**
 * A referenced aggregate does not exist.
 *
 * <p>Lives in the application layer, not the domain, and the distinction is deliberate. A {@link
 * fr.patrimoine.domain.error.DomainException} means "this operation is illegal" &mdash; a business
 * rule refused it. This means "the thing you asked about is not here", which is a lookup concern
 * belonging to whoever does the looking up. The domain never searches for anything; it is handed
 * aggregates already loaded.
 *
 * <p>It exposes the same {@code code()} contract as the domain exceptions so the API layer can map
 * both onto RFC 7807 problem types through one mechanism.
 */
public class ResourceNotFoundException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    private final String resourceType;
    private final String identifier;

    public ResourceNotFoundException(String resourceType, String identifier) {
        super("%s %s was not found".formatted(resourceType, identifier));
        this.resourceType = resourceType;
        this.identifier = identifier;
    }

    public String code() {
        return "resource-not-found";
    }

    public String resourceType() {
        return resourceType;
    }

    public String identifier() {
        return identifier;
    }
}
