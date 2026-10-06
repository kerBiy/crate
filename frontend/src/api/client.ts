import { getSession } from '../auth/session.ts'

/** RFC 9457 Problem Details, as every service returns them (SPEC 7). */
export type Problem = {
  type?: string
  title?: string
  status?: number
  detail?: string
  errors?: { field: string; message: string }[]
}

export class ApiError extends Error {
  readonly status: number
  readonly problem: Problem

  constructor(status: number, problem: Problem) {
    super(problem.detail ?? problem.title ?? `Request failed with ${status}`)
    this.status = status
    this.problem = problem
  }

  /** The problem's slug: "urn:crate:problem:username-taken" → "username-taken". */
  get code() {
    return this.problem.type?.split(':').at(-1)
  }
}

// Set by queryClient.ts, so this file doesn't import the query client (no import cycle).
let onUnauthorized = () => {}

export function setUnauthorizedHandler(handler: () => void) {
  onUnauthorized = handler
}

/**
 * Calls the gateway. Same origin: Vite proxies /api in dev, Caddy in prod.
 * Adds the bearer token when signed in. A 401 anywhere but /auth means the token is missing,
 * expired or invalid, so the user is signed out. On /auth/login a 401 is just a wrong password.
 */
export async function api<T>(path: string, init: RequestInit = {}): Promise<T> {
  const session = getSession()
  const headers = new Headers(init.headers)
  headers.set('Accept', 'application/json')
  if (init.body) headers.set('Content-Type', 'application/json')
  if (session) headers.set('Authorization', `Bearer ${session.accessToken}`)

  const response = await fetch(`/api${path}`, { ...init, headers })

  if (!response.ok) {
    if (response.status === 401 && !path.startsWith('/auth/')) onUnauthorized()
    throw new ApiError(response.status, await problemOf(response))
  }
  if (response.status === 204) return undefined as T
  return response.json() as Promise<T>
}

async function problemOf(response: Response): Promise<Problem> {
  try {
    return await response.json()
  } catch {
    return { status: response.status }
  }
}

/** Validation errors by field name, for forms. */
export function fieldErrors(error: unknown): Record<string, string> {
  if (!(error instanceof ApiError)) return {}
  return Object.fromEntries((error.problem.errors ?? []).map((e) => [e.field, e.message]))
}
