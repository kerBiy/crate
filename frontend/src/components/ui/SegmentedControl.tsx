type SegmentedControlProps<T extends string> = {
  /** What the choice is about, for assistive tech: "Search for", "Theme". */
  label: string
  options: { id: T; label: string }[]
  value: T
  onChange: (value: T) => void
}

/** A few mutually exclusive choices side by side, the chosen one raised. Each option is a toggle button. */
export function SegmentedControl<T extends string>({ label, options, value, onChange }: SegmentedControlProps<T>) {
  return (
    <div role="group" aria-label={label} className="flex max-w-full gap-1 self-start rounded-control border border-border-strong p-1">
      {options.map((option) => {
        const active = option.id === value
        return (
          <button
            key={option.id}
            type="button"
            aria-pressed={active}
            onClick={() => onChange(option.id)}
            className={`h-tap rounded-control px-4 text-body active:bg-pressed ${active ? 'bg-surface-raised font-medium text-text' : 'text-muted'}`}
          >
            {option.label}
          </button>
        )
      })}
    </div>
  )
}
