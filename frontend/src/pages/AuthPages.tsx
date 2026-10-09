import { useState, type FormEvent, type ReactNode } from 'react'
import { Link, useLocation } from 'react-router'
import { ApiError, fieldErrors } from '../api/client.ts'
import { useLogin, useRegister, type Registration } from '../api/queries.ts'
import { Button } from '../components/ui/Button.tsx'
import { Input } from '../components/ui/Input.tsx'

/** Centered form, the wordmark as the only decoration (DESIGN.md section 13). */
function AuthLayout({ title, error, children, footer }: { title: string; error?: string; children: ReactNode; footer: ReactNode }) {
  return (
    <main className="flex min-h-dvh flex-col items-center justify-center px-4 py-12">
      <div className="flex w-full max-w-form flex-col gap-8">
        <p className="font-display text-h3 font-bold text-text" aria-hidden="true">
          crate
        </p>
        <div className="flex flex-col gap-6">
          <h1 className="font-narrow font-display text-h1 font-semibold text-balance text-text">{title}</h1>
          {error && (
            <p role="alert" className="text-body text-danger">
              {error}
            </p>
          )}
          {children}
        </div>
        <p className="text-body text-muted">{footer}</p>
      </div>
    </main>
  )
}

/** Keeps "where to go after signing in" when switching between Login and Register. */
function AuthLink({ to, children }: { to: string; children: string }) {
  const { state } = useLocation()
  return (
    <Link to={to} state={state} className="font-medium text-accent-text underline">
      {children}
    </Link>
  )
}

const unavailable = "Couldn't reach the server. Try again."

export function LoginPage() {
  const login = useLogin()
  const [form, setForm] = useState({ login: '', password: '' })
  const fields = sentenceCase(fieldErrors(login.error))

  function submit(event: FormEvent) {
    event.preventDefault()
    // On success the session changes and RedirectIfSignedIn moves on; nothing to do here.
    login.mutate(form)
  }

  let error: string | undefined
  if (login.error instanceof ApiError && login.error.status === 401) error = 'Wrong username or password.'
  else if (login.isError && Object.keys(fields).length === 0) error = unavailable

  return (
    <AuthLayout
      title="Log in"
      error={error}
      footer={
        <>
          New here? <AuthLink to="/register">Create an account</AuthLink>
        </>
      }
    >
      <form onSubmit={submit} noValidate className="flex flex-col gap-4">
        <Input
          label="Username or email"
          autoComplete="username"
          autoFocus
          value={form.login}
          onChange={(e) => setForm({ ...form, login: e.target.value })}
          error={fields.login}
        />
        <Input
          label="Password"
          type="password"
          autoComplete="current-password"
          value={form.password}
          onChange={(e) => setForm({ ...form, password: e.target.value })}
          error={fields.password}
        />
        <Button type="submit" variant="primary" loading={login.isPending} className="mt-2">
          Log in
        </Button>
      </form>
    </AuthLayout>
  )
}

export function RegisterPage() {
  const register = useRegister()
  const [form, setForm] = useState<Registration>({ username: '', email: '', password: '', inviteCode: '' })
  const fields = { ...sentenceCase(fieldErrors(register.error)), ...conflicts(register.error) }

  function submit(event: FormEvent) {
    event.preventDefault()
    register.mutate(form)
  }

  const error = register.isError && Object.keys(fields).length === 0 ? unavailable : undefined
  const bind = (name: keyof Registration) => ({
    value: form[name],
    onChange: (e: { target: { value: string } }) => setForm({ ...form, [name]: e.target.value }),
    error: fields[name],
  })

  return (
    <AuthLayout
      title="Create an account"
      error={error}
      footer={
        <>
          Have an account? <AuthLink to="/login">Log in</AuthLink>
        </>
      }
    >
      <form onSubmit={submit} noValidate className="flex flex-col gap-4">
        <Input label="Username" autoComplete="username" autoFocus hint="Letters, digits and underscores." {...bind('username')} />
        <Input label="Email" type="email" autoComplete="email" {...bind('email')} />
        <Input label="Password" type="password" autoComplete="new-password" hint="At least 10 characters." {...bind('password')} />
        <Input label="Invite code" autoComplete="off" hint="Ask a friend who's already in." {...bind('inviteCode')} />
        <Button type="submit" variant="primary" loading={register.isPending} className="mt-2">
          Create account
        </Button>
      </form>
    </AuthLayout>
  )
}

/** Problems that belong to one field, shown on that field instead of above the form. */
function conflicts(error: unknown): Record<string, string> {
  if (!(error instanceof ApiError)) return {}
  switch (error.code) {
    case 'invalid-invite-code':
      return { inviteCode: "That invite code doesn't work. Check it with whoever sent it." }
    case 'username-taken':
      return { username: 'That username is taken.' }
    case 'email-taken':
      return { email: 'An account with that email already exists.' }
    case 'account-exists':
      return { username: 'That username or email is already registered.' }
    default:
      return {}
  }
}

/** "must be at least 10 characters" → "Must be at least 10 characters." */
function sentenceCase(errors: Record<string, string>) {
  return Object.fromEntries(
    Object.entries(errors).map(([field, message]) => [field, `${message[0].toUpperCase()}${message.slice(1)}.`]),
  )
}
