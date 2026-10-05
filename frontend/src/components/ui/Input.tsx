import { useId, type ComponentProps } from 'react'

type InputProps = ComponentProps<'input'> & {
  label: string
  /** Short help under the field. Hidden while there is an error. */
  hint?: string
  error?: string
}

/** Labelled text field. The error replaces the hint and is announced with the field. */
export function Input({ label, hint, error, id, className = '', ...rest }: InputProps) {
  const fallbackId = useId()
  const inputId = id ?? fallbackId
  const noteId = `${inputId}-note`
  const note = error ?? hint

  return (
    <div className={`flex flex-col gap-1 ${className}`}>
      <label htmlFor={inputId} className="text-meta font-medium text-text">
        {label}
      </label>
      <input
        id={inputId}
        aria-invalid={error ? true : undefined}
        aria-describedby={note ? noteId : undefined}
        className={`h-tap rounded-control border bg-surface px-3 text-body text-text placeholder:text-muted disabled:cursor-not-allowed disabled:bg-bg disabled:text-muted ${
          error ? 'border-danger' : 'border-border'
        }`}
        {...rest}
      />
      {note && (
        <p id={noteId} className={`text-meta ${error ? 'text-danger' : 'text-muted'}`}>
          {note}
        </p>
      )}
    </div>
  )
}
