import { useId, type ComponentProps } from 'react'

type InputProps = Omit<ComponentProps<'input'>, 'size'> & {
  label: string
  /** lg: the one main field of a page, such as search. */
  size?: 'md' | 'lg'
  /** Short help under the field. Hidden while there is an error. */
  hint?: string
  error?: string
}

/** Labelled text field. The error replaces the hint and is announced with the field. */
export function Input({ label, hint, error, size = 'md', id, className = '', ...rest }: InputProps) {
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
        className={`rounded-control border bg-surface text-text ${
          size === 'lg' ? 'h-16 px-4 text-h4' : 'h-tap px-3 text-body'
        } placeholder:text-muted disabled:cursor-not-allowed disabled:bg-bg disabled:text-muted ${
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
