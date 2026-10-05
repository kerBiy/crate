type VinylProps = { color: string }

const grooves = [45, 41, 37.5, 33, 28.5]
// Radius of the tinted center label, in a 100-unit disc.
const label = 16

/** The record that lives in the sleeve. Decorative, so hidden from assistive tech. */
export function Vinyl({ color }: VinylProps) {
  return (
    <svg viewBox="0 0 100 100" className="block size-full" aria-hidden="true">
      <circle cx="50" cy="50" r="49" fill="var(--vinyl-disc)" stroke="var(--vinyl-rim)" strokeWidth="0.6" />
      {grooves.map((r) => (
        <circle key={r} cx="50" cy="50" r={r} fill="none" stroke="var(--vinyl-groove)" strokeWidth="0.4" />
      ))}
      <circle cx="50" cy="50" r={label} fill={color} />
      <circle cx="50" cy="50" r={label - 1.6} fill="none" stroke="var(--vinyl-disc)" strokeOpacity="0.18" strokeWidth="0.5" />
      {/* Off-center print on the label, so a spin is visible. */}
      <rect x={50 - label * 0.45} y={50 - label * 0.55} width={label * 0.9} height="1.4" rx="0.7" fill="var(--vinyl-disc)" fillOpacity="0.3" />
      <rect x={50 - label * 0.3} y={50 - label * 0.55 + 3} width={label * 0.6} height="1.1" rx="0.55" fill="var(--vinyl-disc)" fillOpacity="0.22" />
      <circle cx="50" cy="50" r="1.5" fill="var(--bg)" />
    </svg>
  )
}
