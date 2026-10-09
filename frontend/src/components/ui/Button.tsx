import { CircleNotch } from '@phosphor-icons/react'
import type { ComponentProps, ReactNode } from 'react'

export type ButtonVariant = 'primary' | 'secondary' | 'ghost'

type ButtonProps = ComponentProps<'button'> & {
  variant?: ButtonVariant
  icon?: ReactNode
  /** Shows a spinner in place of the icon and blocks clicks; the label stays so the width holds. */
  loading?: boolean
}

// Pressed shows on pointer-down, with no transition (DESIGN.md section 7).
const looks: Record<ButtonVariant, string> = {
  primary: 'bg-accent text-on-accent active:bg-accent-pressed',
  secondary: 'border border-border-strong bg-surface text-text active:bg-pressed',
  ghost: 'text-text active:bg-pressed',
}

// Same for every variant: quiet, and clearly not the thing to press.
const disabledLook =
  'inline-flex h-tap cursor-not-allowed items-center justify-center gap-2 rounded-control border border-border px-4 text-body font-medium text-muted'

/** Button look for things that aren't buttons, such as a link that acts as the main action. */
export function buttonStyles(variant: ButtonVariant = 'secondary') {
  return `inline-flex h-tap items-center justify-center gap-2 rounded-control px-4 text-body font-medium ${looks[variant]}`
}

export function Button({
  variant = 'secondary',
  icon,
  loading = false,
  disabled,
  children,
  className = '',
  ...rest
}: ButtonProps) {
  return (
    <button
      type="button"
      disabled={disabled || loading}
      aria-busy={loading || undefined}
      className={`${disabled && !loading ? disabledLook : buttonStyles(variant)} ${className}`}
      {...rest}
    >
      {loading ? <CircleNotch size="1.25em" className="spinner" aria-hidden="true" /> : icon}
      {children}
    </button>
  )
}
