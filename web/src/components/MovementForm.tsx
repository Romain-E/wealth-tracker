import { useId, useState, type SubmitEvent } from "react";
import { useRecordMovement } from "../api/hooks";
import { toIsoDate } from "../format";
import { ErrorPanel } from "./ErrorPanel";

type Kind = "DEPOSIT" | "WITHDRAWAL";

const KINDS: { value: Kind; label: string }[] = [
  { value: "DEPOSIT", label: "Dépôt" },
  { value: "WITHDRAWAL", label: "Retrait" },
];

interface Props {
  accountId: string;
  currency: string;
}

/**
 * Records a deposit or a withdrawal. The form checks only the shape of the input; whether the
 * movement is allowed (ceiling, available cash) is the server's decision, and its refusal is shown
 * as it stated it.
 */
export function MovementForm({ accountId, currency }: Props) {
  const record = useRecordMovement(accountId);
  const [kind, setKind] = useState<Kind>("DEPOSIT");
  const [amount, setAmount] = useState("");
  const [date, setDate] = useState(() => toIsoDate(new Date()));
  const headingId = useId();
  const today = toIsoDate(new Date());

  function submit(event: SubmitEvent<HTMLFormElement>) {
    event.preventDefault();
    record.mutate(
      { type: kind, date, amount: { amount: Number(amount.replace(",", ".")), currency } },
      {
        onSuccess: () => {
          setAmount("");
        },
      },
    );
  }

  return (
    <section className="card" aria-labelledby={headingId}>
      <h2 id={headingId}>Enregistrer un mouvement</h2>
      <form className="movement-form" onSubmit={submit}>
        <fieldset className="segmented">
          <legend className="visually-hidden">Type de mouvement</legend>
          {KINDS.map((option) => (
            <label key={option.value}>
              <input
                type="radio"
                name={`${headingId}-kind`}
                value={option.value}
                checked={kind === option.value}
                onChange={() => {
                  setKind(option.value);
                }}
              />
              <span>{option.label}</span>
            </label>
          ))}
        </fieldset>
        <label className="field">
          Montant ({currency})
          <input
            inputMode="decimal"
            required
            // Cents at most, as the API requires; a comma is accepted as the decimal separator.
            pattern="\d+([.,]\d{1,2})?"
            title="Un montant positif, avec au plus deux décimales"
            value={amount}
            onChange={(event) => {
              setAmount(event.target.value);
            }}
          />
        </label>
        <label className="field">
          Date
          <input
            type="date"
            required
            max={today}
            value={date}
            onChange={(event) => {
              setDate(event.target.value);
            }}
          />
        </label>
        <button type="submit" className="primary-button" disabled={record.isPending}>
          {record.isPending ? "Enregistrement…" : "Enregistrer"}
        </button>
      </form>
      {record.isError && <ErrorPanel error={record.error} />}
      {record.isSuccess && (
        <p className="success" role="status">
          Mouvement enregistré.
        </p>
      )}
    </section>
  );
}
