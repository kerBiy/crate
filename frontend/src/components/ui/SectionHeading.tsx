/** A section inside a page ("Ratings", "Reviews"): 24 semibold, 24 above its content. */
export function SectionHeading({ children }: { children: string }) {
  return <h2 className="mb-6 font-display text-h3 font-semibold text-text">{children}</h2>
}
