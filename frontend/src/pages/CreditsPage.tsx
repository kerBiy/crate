import type { ReactNode } from 'react'
import { Link } from 'react-router'
import { PageHeader, pageFrame } from '../components/ui/PageHeader.tsx'
import { SectionHeading } from '../components/ui/SectionHeading.tsx'

/** A link to another site, styled like the credits on the album page. */
function External({ href, children }: { href: string; children: ReactNode }) {
  return (
    <a href={href} target="_blank" rel="noreferrer" className="font-medium text-accent-text underline">
      {children}
    </a>
  )
}

/**
 * /credits, reached from Settings: where Crate's outside data comes from, and under which license.
 * The license notice for the Wikipedia descriptions lives here, while each description links to its
 * article (ADR 014). Nothing to load, so no data states.
 */
export function CreditsPage() {
  return (
    <main className={pageFrame}>
      <header className="flex flex-col">
        <Link to="/settings" className="flex min-h-tap items-center self-start rounded-control text-body text-muted">
          Settings
        </Link>
        <PageHeader title="Credits" />
      </header>
      <div className="flex max-w-prose flex-col">
        <Source heading="Wikipedia" first>
          Album descriptions are the first paragraph of the album's article on English{' '}
          <External href="https://en.wikipedia.org/">Wikipedia</External>, shown unchanged and linked to the
          article. They are available under{' '}
          <External href="https://creativecommons.org/licenses/by-sa/4.0/">CC BY-SA 4.0</External>.
        </Source>
        <Source heading="MusicBrainz">
          Albums, artists, tracklists and release facts come from{' '}
          <External href="https://musicbrainz.org/">MusicBrainz</External>, under{' '}
          <External href="https://creativecommons.org/publicdomain/zero/1.0/">CC0</External>.
        </Source>
        <Source heading="Cover Art Archive">
          Album covers come from the{' '}
          <External href="https://coverartarchive.org/">Cover Art Archive</External>.
        </Source>
        <Source heading="ListenBrainz">
          Listener and listen counts come from{' '}
          <External href="https://listenbrainz.org/">ListenBrainz</External>, under{' '}
          <External href="https://creativecommons.org/publicdomain/zero/1.0/">CC0</External>.
        </Source>
      </div>
    </main>
  )
}

function Source({ heading, first, children }: { heading: string; first?: boolean; children: ReactNode }) {
  return (
    <section className={first ? 'mt-8 lg:mt-12' : 'mt-12 lg:mt-16'}>
      <SectionHeading>{heading}</SectionHeading>
      <p className="text-body text-muted">{children}</p>
    </section>
  )
}
