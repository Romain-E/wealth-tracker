import { Link } from "react-router";
import { usePageTitle } from "../usePageTitle";

export function NotFoundPage() {
  usePageTitle("Page introuvable");
  return (
    <section className="page-head">
      <h1>Page introuvable</h1>
      <p>
        <Link to="/">Retour au tableau de bord</Link>
      </p>
    </section>
  );
}
