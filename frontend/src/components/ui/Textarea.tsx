import { useId, type ComponentProps } from 'react'

type TextareaProps = ComponentProps<'textarea'> & {
  label: string
  /** Short help under the field. Hidden while there is an error. */
  hint?: string
  error?: string
}

/** Labelled multi-line text field, for longer writing such as a review. Same contract as Input. */
export function Textarea({ label, hint, error, id, rows = 5, className = '', ...rest }: TextareaProps) {
  const fallbackId = useId()
  const fieldId = id ?? fallbackId
  const noteId = `${fieldId}-note`
  const note = error ?? hint

  return (
    <div className={`flex flex-col gap-1 ${className}`}>
      <label htmlFor={fieldId} className="text-meta font-medium text-text">
        {label}
      </label>
      <textarea
        id={fieldId}
        rows={rows}
        aria-invalid={error ? true : undefined}
        aria-describedby={note ? noteId : undefined}
        className={`resize-y rounded-control border bg-surface px-3 py-2 text-body text-text placeholder:text-muted disabled:cursor-not-allowed disabled:bg-bg disabled:text-muted ${
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
