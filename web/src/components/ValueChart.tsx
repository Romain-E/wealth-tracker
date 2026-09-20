import { useId, useMemo, useState, type KeyboardEvent, type PointerEvent } from "react";
import { formatCompactMoney, formatDate, formatMoney, formatMonth, parseIsoDate } from "../format";
import { niceTicks, timeTicks } from "./scales";
import { useElementWidth } from "./useElementWidth";

export interface ValuePoint {
  date: string;
  value: number;
}

interface Props {
  title: string;
  points: readonly ValuePoint[];
  currency: string;
  /** True while a newer window is loading: the previous curve stays, dimmed. */
  dimmed?: boolean;
}

const PLOT_HEIGHT = 220;
// Right margin leaves room for the direct label on the last point.
const MARGIN = { top: 12, right: 88, bottom: 28, left: 64 } as const;

/**
 * The value of a portfolio or an account over time: one series, so one colour and no legend box;
 * the caption says what is plotted.
 *
 * The y axis starts at zero. The area under the line encodes an amount, and cutting the axis would
 * turn a 3 % move into a cliff. Hovering, or the arrow keys once the chart has focus, moves a
 * crosshair to the nearest date; the same values are always available in the table view.
 */
export function ValueChart({ title, points, currency, dimmed = false }: Props) {
  const [frameRef, width] = useElementWidth(640);
  const [active, setActive] = useState<number | null>(null);
  const [showTable, setShowTable] = useState(false);
  const captionId = useId();

  const geometry = useMemo(() => {
    if (points.length < 2) {
      return null;
    }
    const plotWidth = Math.max(width - MARGIN.left - MARGIN.right, 120);
    const times = points.map((point) => parseIsoDate(point.date).getTime());
    const start = times[0] ?? 0;
    const end = times[times.length - 1] ?? start;
    const span = end - start || 1;
    const yTicks = niceTicks(Math.max(...points.map((point) => point.value)));
    const top = yTicks[yTicks.length - 1] ?? 1;

    const x = (time: number) => MARGIN.left + ((time - start) / span) * plotWidth;
    const y = (value: number) => MARGIN.top + PLOT_HEIGHT - (value / top) * PLOT_HEIGHT;

    const coordinates = points.map((point, index) => ({
      x: x(times[index] ?? start),
      y: y(point.value),
    }));
    const line = coordinates
      .map(
        ({ x: px, y: py }, index) => `${index === 0 ? "M" : "L"}${px.toFixed(1)},${py.toFixed(1)}`,
      )
      .join("");
    const baseline = y(0);
    const area = `${line}L${x(end).toFixed(1)},${baseline}L${x(start).toFixed(1)},${baseline}Z`;

    return {
      coordinates,
      line,
      area,
      baseline,
      right: MARGIN.left + plotWidth,
      yTicks: yTicks.map((value) => ({ value, y: y(value) })),
      // About one month label per 90px, so labels never run into each other on a phone.
      xTicks: timeTicks(start, end, Math.min(6, Math.max(2, Math.floor(plotWidth / 90)))).map(
        (time) => ({ time, x: x(time) }),
      ),
    };
  }, [points, width]);

  if (geometry === null) {
    return (
      <figure className="chart">
        <figcaption>{title}</figcaption>
        <p className="empty">Pas encore assez d’historique pour tracer une courbe.</p>
      </figure>
    );
  }

  const last = points.length - 1;
  const lastPoint = geometry.coordinates[last];
  const activePoint = active === null ? undefined : points[active];
  const activeCoordinates = active === null ? undefined : geometry.coordinates[active];

  function nearestIndex(pointerX: number): number {
    let best = 0;
    geometry?.coordinates.forEach((coordinate, index) => {
      const bestX = geometry.coordinates[best]?.x ?? 0;
      if (Math.abs(coordinate.x - pointerX) < Math.abs(bestX - pointerX)) {
        best = index;
      }
    });
    return best;
  }

  function onPointerMove(event: PointerEvent<SVGSVGElement>) {
    const bounds = event.currentTarget.getBoundingClientRect();
    const scale = bounds.width > 0 ? width / bounds.width : 1;
    setActive(nearestIndex((event.clientX - bounds.left) * scale));
  }

  function onKeyDown(event: KeyboardEvent<SVGSVGElement>) {
    const moves: Record<string, (current: number) => number> = {
      ArrowLeft: (current) => Math.max(current - 1, 0),
      ArrowRight: (current) => Math.min(current + 1, last),
      Home: () => 0,
      End: () => last,
    };
    const move = moves[event.key];
    if (move) {
      event.preventDefault();
      setActive((current) => move(current ?? last));
    }
  }

  const svgHeight = MARGIN.top + PLOT_HEIGHT + MARGIN.bottom;

  return (
    <figure className="chart" aria-labelledby={captionId}>
      <figcaption id={captionId}>{title}</figcaption>
      <div ref={frameRef} className={dimmed ? "chart-frame dimmed" : "chart-frame"}>
        <svg
          viewBox={`0 0 ${width} ${svgHeight}`}
          height={svgHeight}
          role="img"
          aria-label={`${title}, du ${formatDate(points[0]?.date ?? "")} au ${formatDate(
            points[last]?.date ?? "",
          )}. Flèches gauche et droite pour parcourir les valeurs.`}
          tabIndex={0}
          onPointerMove={onPointerMove}
          onPointerLeave={() => {
            setActive(null);
          }}
          onKeyDown={onKeyDown}
          onBlur={() => {
            setActive(null);
          }}
        >
          {geometry.yTicks.map((tick) => (
            <g key={tick.value}>
              <line
                className={tick.value === 0 ? "baseline" : "gridline"}
                x1={MARGIN.left}
                x2={geometry.right}
                y1={tick.y}
                y2={tick.y}
              />
              <text className="tick" x={MARGIN.left - 8} y={tick.y} dy="0.32em" textAnchor="end">
                {formatCompactMoney(tick.value, currency)}
              </text>
            </g>
          ))}
          {geometry.xTicks.map((tick) => (
            <text
              key={tick.time}
              className="tick"
              x={tick.x}
              y={geometry.baseline + 20}
              textAnchor="middle"
            >
              {formatMonth(new Date(tick.time))}
            </text>
          ))}

          <path className="series-area" d={geometry.area} />
          <path className="series-line" d={geometry.line} />

          {lastPoint && (
            <>
              <circle className="series-dot" cx={lastPoint.x} cy={lastPoint.y} r={4} />
              <text className="end-label" x={lastPoint.x + 10} y={lastPoint.y} dy="0.32em">
                {formatCompactMoney(points[last]?.value ?? 0, currency)}
              </text>
            </>
          )}

          {activeCoordinates && (
            <>
              <line
                className="crosshair"
                x1={activeCoordinates.x}
                x2={activeCoordinates.x}
                y1={MARGIN.top}
                y2={geometry.baseline}
              />
              <circle
                className="series-dot"
                cx={activeCoordinates.x}
                cy={activeCoordinates.y}
                r={5}
              />
            </>
          )}
        </svg>

        {activePoint && activeCoordinates && (
          <div
            className="tooltip"
            role="status"
            style={{
              left: `${(Math.min(Math.max(activeCoordinates.x, 80), width - 80) / width) * 100}%`,
              top: 0,
            }}
          >
            <strong>
              <span className="tooltip-key" aria-hidden="true" />
              {formatMoney(activePoint.value, currency)}
            </strong>
            <span>{formatDate(activePoint.date)}</span>
          </div>
        )}
      </div>

      <div className="chart-actions">
        <button
          type="button"
          className="link-button"
          aria-expanded={showTable}
          onClick={() => {
            setShowTable((shown) => !shown);
          }}
        >
          {showTable ? "Masquer le tableau" : "Afficher les valeurs en tableau"}
        </button>
      </div>

      {showTable && (
        <div className="table-wrap">
          <table>
            <caption className="visually-hidden">{title}</caption>
            <thead>
              <tr>
                <th scope="col">Date</th>
                <th scope="col" className="num">
                  Valeur
                </th>
              </tr>
            </thead>
            <tbody>
              {points.map((point) => (
                <tr key={point.date}>
                  <td>{formatDate(point.date)}</td>
                  <td className="num">{formatMoney(point.value, currency)}</td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      )}
    </figure>
  );
}
