import { ApiError } from "../api/client";

/**
 * An error as the API explained it. The problem's own detail is shown ("Livret A payments are
 * capped at…") rather than a generic apology, and the correlation id is shown as a reference, so a
 * support request can go straight to the matching log lines.
 */
export function ErrorPanel({ error }: { error: Error }) {
  const problem = error instanceof ApiError ? error.problem : undefined;
  const status = error instanceof ApiError ? error.status : undefined;
  const title =
    status === 404
      ? "Introuvable"
      : status === 422
        ? "Opération refusée"
        : status !== undefined && status < 500
          ? "Demande invalide"
          : "Impossible de joindre le serveur";

  return (
    <div className="error-panel" role="alert">
      <p className="error-title">{title}</p>
      {problem?.detail && <p>{problem.detail}</p>}
      {problem?.errors && problem.errors.length > 0 && (
        <ul>
          {problem.errors.map((fieldError) => (
            <li key={fieldError.field}>
              {fieldError.field} : {fieldError.message}
            </li>
          ))}
        </ul>
      )}
      {problem?.correlationId && (
        <p className="secondary">
          Référence : <code>{problem.correlationId}</code>
        </p>
      )}
    </div>
  );
}
