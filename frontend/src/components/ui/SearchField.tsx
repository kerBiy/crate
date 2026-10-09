import { MagnifyingGlass, X } from '@phosphor-icons/react'
import { useId, useRef, type ComponentProps } from 'react'

type SearchFieldProps = Omit<ComponentProps<'input'>, 'value' | 'onChange' | 'onSubmit' | 'size' | 'type'> & {
  /** Visible label above the field: what to type ("Album or artist"). */
  label: string
  value: string
  onChange: (value: string) => void
  /** Enter: search now, without waiting for the pause after typing. */
  onSubmit?: () => void
}

/**
 * The one field of the Search page: a magnifier inside, a clear button once there is text. Clearing
 * puts focus back in the field, so typing can go on. Escape clears too (the browser's own behavior).
 */
export function SearchField({ label, value, onChange, onSubmit, id, className = '', ...rest }: SearchFieldProps) {
  const fallbackId = useId()
  const inputId = id ?? fallbackId
  const input = useRef<HTMLInputElement>(null)

  return (
    <div className={`flex flex-col gap-2 ${className}`}>
      <label htmlFor={inputId} className="text-meta font-medium text-muted">
        {label}
      </label>
      <div className="relative">
        <MagnifyingGlass
          aria-hidden="true"
          size="1.5em"
          className="pointer-events-none absolute top-1/2 left-4 -translate-y-1/2 text-muted"
        />
        <input
          ref={input}
          id={inputId}
          type="search"
          value={value}
          onChange={(event) => onChange(event.target.value)}
          onKeyDown={(event) => event.key === 'Enter' && onSubmit?.()}
          className="h-16 w-full rounded-control border border-border-strong bg-surface pr-12 pl-12 text-h4 text-text placeholder:text-muted"
          {...rest}
        />
        {value && (
          <button
            type="button"
            aria-label="Clear search"
            onClick={() => {
              onChange('')
              input.current?.focus()
            }}
            className="absolute top-1/2 right-2 flex size-tap -translate-y-1/2 items-center justify-center rounded-full text-muted active:bg-pressed"
          >
            <X size="1.25em" aria-hidden="true" />
          </button>
        )}
      </div>
    </div>
  )
}
