import { Link, Outlet, isRouteErrorResponse, useRouteError } from "react-router";

export function Layout() {
  return (
    <>
      <a className="skip-link" href="#main">
        Aller au contenu
      </a>
      <header className="app-header">
        <div className="container">
          <Link to="/" className="brand">
            Patrimoine
          </Link>
        </div>
      </header>
      <main id="main" className="container">
        <Outlet />
      </main>
    </>
  );
}

/** Last line of defence for a rendering error: the page says so instead of going blank. */
export function RouteError() {
  const error = useRouteError();
  const message = isRouteErrorResponse(error)
    ? `${error.status} ${error.statusText}`
    : error instanceof Error
      ? error.message
      : "Erreur inconnue";
  return (
    <main className="container">
      <div className="error-panel" role="alert">
        <p className="error-title">Cette page n’a pas pu s’afficher</p>
        <p>{message}</p>
        <p>
          <Link to="/">Retour au tableau de bord</Link>
        </p>
      </div>
    </main>
  );
}
