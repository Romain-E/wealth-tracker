import { useId } from "react";
import { WINDOWS, type WindowId } from "./chartWindow";

interface Props {
  value: WindowId;
  onChange: (id: WindowId) => void;
}

/** Period presets as a single row of radio buttons: arrow keys move between them. */
export function WindowPicker({ value, onChange }: Props) {
  const name = useId();
  return (
    <div className="filters">
      <fieldset className="segmented">
        <legend className="visually-hidden">Période affichée</legend>
        {WINDOWS.map((window) => (
          <label key={window.id}>
            <input
              type="radio"
              name={name}
              value={window.id}
              checked={value === window.id}
              onChange={() => {
                onChange(window.id);
              }}
            />
            <span>{window.label}</span>
          </label>
        ))}
      </fieldset>
    </div>
  );
}
