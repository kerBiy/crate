import { BookmarkSimple, PaperPlaneTilt } from '@phosphor-icons/react'
import { useState, type ReactNode } from 'react'
import { AlbumTile, AlbumTileSkeleton } from '../components/ui/AlbumTile.tsx'
import { AppShell, type Section as NavSection } from '../components/ui/AppShell.tsx'
import { Avatar, AvatarStack } from '../components/ui/Avatar.tsx'
import { Button, type ButtonVariant } from '../components/ui/Button.tsx'
import { Cover } from '../components/ui/Cover.tsx'
import { FeedRow, FeedRowSkeleton } from '../components/ui/FeedRow.tsx'
import { FollowButton } from '../components/ui/FollowButton.tsx'
import { Histogram } from '../components/ui/Histogram.tsx'
import { Input } from '../components/ui/Input.tsx'
import { PersonRow, PersonRowSkeleton } from '../components/ui/PersonRow.tsx'
import { RatingInput } from '../components/ui/RatingInput.tsx'
import { RatingStars } from '../components/ui/RatingStars.tsx'
import { Skeleton } from '../components/ui/Skeleton.tsx'
import { EmptyState, ErrorState } from '../components/ui/States.tsx'
import { Textarea } from '../components/ui/Textarea.tsx'
import { ToastMessage, ToastRegion, useToast } from '../components/ui/Toast.tsx'
import { Vinyl } from '../components/ui/Vinyl.tsx'
import { albums, coverUrl, everyone } from '../lab/albums.ts'
import { ThemeToggle } from './ThemeToggle.tsx'

const variants: ButtonVariant[] = ['primary', 'secondary', 'ghost']
const sections: NavSection[] = ['feed', 'search', 'profile']
const sectionNames: Record<NavSection, string> = { feed: 'Feed', search: 'Search', profile: 'Profile' }

/** Every base component in every state, for checking both themes at a glance. Development only. */
export function ComponentsPage() {
  const [tab, setTab] = useState<NavSection>('feed')
  const [rating, setRating] = useState(0)
  const [following, setFollowing] = useState(false)
  const toast = useToast()
  const [ok, kid] = albums

  return (
    <AppShell current={tab}>
      <div className="sticky top-12 z-10 border-b border-border bg-bg lg:top-16">
        <div className="mx-auto flex max-w-content flex-wrap items-center justify-between gap-3 px-4 py-3 lg:px-6">
          <h1 className="font-display text-h3 font-semibold text-text">Components</h1>
          <ThemeToggle />
        </div>
      </div>

      <main className="mx-auto flex max-w-content flex-col px-4 pb-16 lg:px-6">
        <Block name="Button" use="One primary per view; secondary for the rest; ghost inside rows.">
          {variants.map((variant) => (
            <Cell key={variant} label={sentence(variant)}>
              <div className="flex flex-wrap gap-3">
                <Button variant={variant}>Follow</Button>
                <Button
                  variant={variant}
                  icon={<BookmarkSimple size="1.25em" className={variant === 'primary' ? '' : 'text-accent'} />}
                >
                  Listen later
                </Button>
              </div>
            </Cell>
          ))}
          <Cell label="Disabled">
            <Button disabled icon={<PaperPlaneTilt size="1.25em" />}>
              Send to a friend
            </Button>
          </Cell>
          <Cell label="Loading">
            <div className="flex flex-wrap gap-3">
              <Button variant="primary" loading>
                Follow
              </Button>
              <Button loading>Try again</Button>
            </div>
          </Cell>
        </Block>

        <Block name="Input" use="Every text field, always with a visible label.">
          <Cell label="Empty">
            <Input label="Username" placeholder="ana" />
          </Cell>
          <Cell label="With hint">
            <Input label="Invite code" hint="Ask a friend who's already in." />
          </Cell>
          <Cell label="Error">
            <Input label="Password" type="password" defaultValue="short" error="Use at least 8 characters." />
          </Cell>
          <Cell label="Disabled">
            <Input label="Email" defaultValue="ana@example.com" disabled />
          </Cell>
          <Cell label="Large (search)">
            <Input label="Search" type="search" size="lg" hint="Album or artist" />
          </Cell>
        </Block>

        <Block name="Textarea" use="Longer writing, such as a review. Same label, hint and error as Input.">
          <Cell label="With hint">
            <Textarea label="Your review" hint="Up to 5000 characters." maxLength={5000} />
          </Cell>
          <Cell label="Filled">
            <Textarea label="Your review" defaultValue="The best one, no debate. Let Down still gets me every time." />
          </Cell>
          <Cell label="Error">
            <Textarea label="Your review" error="Keep it under 5000 characters." />
          </Cell>
          <Cell label="Disabled">
            <Textarea label="Your review" defaultValue="A grower. Give it three listens." disabled />
          </Cell>
        </Block>

        <Block name="RatingStars" use="Showing a rating someone gave. Sized by the text around it.">
          {[0, 2.5, 4.5, 5].map((value) => (
            <Cell key={value} label={value === 0 ? 'Not rated' : `${value}`}>
              <RatingStars value={value} className="text-h4" />
            </Cell>
          ))}
          <Cell label="Inline, meta size">
            <p className="flex items-center gap-2 text-meta text-text">
              Ana <RatingStars value={4} />
            </p>
          </Cell>
        </Block>

        <Block name="RatingInput" use="Giving a rating. Arrows move half a star, Enter confirms.">
          <Cell label={rating ? `Rated ${rating}` : 'Unrated, try it'}>
            <RatingInput label="Rate OK Computer" value={rating} onRate={setRating} />
          </Cell>
          <Cell label="Rated">
            <RatingInput label="Rate Kid A" value={3.5} onRate={() => {}} />
          </Cell>
          <Cell label="Disabled">
            <RatingInput label="Rate In Rainbows" value={4} onRate={() => {}} disabled />
          </Cell>
        </Block>

        <Block
          name="Cover"
          use="Album art at any size. Pulses until its image loads, then fades in. Falls back to initials when missing."
        >
          <Cell label="Image (throttle the network to see the pulse)">
            <div className="w-cover max-w-full">
              <Cover src={coverUrl(ok.mbid, 500)} title={ok.title} artist={ok.artist} eager />
            </div>
          </Cell>
          <Cell label="Missing cover">
            <div className="w-cover max-w-full">
              <Cover title="Songs for the Night Bus" artist="Unknown" />
            </div>
          </Cell>
        </Block>

        <Block name="Vinyl" use="Only with a cover of 96 px or more: grid tiles and the album page.">
          {albums.slice(0, 3).map((album) => (
            <Cell key={album.mbid} label={`Label tint from ${album.title}`}>
              <div className="w-1/3">
                <Vinyl color={album.dominantColor} />
              </div>
            </Cell>
          ))}
        </Block>

        <Block
          name="AlbumTile"
          use="Cover grids. Hover or focus a tile to see the record, once its cover is in. First row eager, the rest lazy."
        >
          <div className="col-span-full">
            <ul className="grid grid-cols-3 gap-3 md:grid-cols-4 lg:grid-cols-6">
              <li>
                <AlbumTileSkeleton />
              </li>
              {albums.map((album, index) => (
                <li key={album.mbid}>
                  <AlbumTile
                    eager={index < 6}
                    to={`/lab/album/${album.mbid}`}
                    title={album.title}
                    artist={album.artist}
                    src={coverUrl(album.mbid, 250)}
                    color={album.dominantColor}
                  />
                </li>
              ))}
              <li>
                <AlbumTile to="/dev/components" title="Songs for the Night Bus" artist="Unknown" color={kid.dominantColor} />
              </li>
            </ul>
          </div>
        </Block>

        <Block name="Histogram" use="Ratings spread on the album page. Hover, tap or focus a bar.">
          <Cell label="Many ratings">
            <HistogramFrame counts={ok.histogram} />
          </Cell>
          <Cell label="Split opinions">
            <HistogramFrame counts={[9, 2, 1, 0, 1, 2, 3, 4, 6, 12]} />
          </Cell>
          <Cell label="One rating">
            <HistogramFrame counts={[0, 0, 0, 0, 0, 0, 0, 1, 0, 0]} />
          </Cell>
          <Cell label="No ratings">
            <HistogramFrame counts={[0, 0, 0, 0, 0, 0, 0, 0, 0, 0]} />
          </Cell>
        </Block>

        <Block name="Avatar" use="A person next to their name. Small in dense rows.">
          <Cell label="Default">
            <Avatar name="Ana" />
          </Cell>
          <Cell label="Small">
            <Avatar name="Radu" size="sm" />
          </Cell>
        </Block>

        <Block name="AvatarStack" use="A group of people in one line, such as friends who listened.">
          <Cell label="Two">
            <AvatarStack names={everyone.slice(0, 2)} />
          </Cell>
          <Cell label="Four">
            <AvatarStack names={everyone.slice(0, 4)} />
          </Cell>
          <Cell label="Seven, four shown">
            <AvatarStack names={everyone.slice(0, 7)} />
          </Cell>
        </Block>

        <Block name="FollowButton" use="On a profile. Pops once into Following; press Following to unfollow.">
          <Cell label="Try it">
            <FollowButton following={following} onToggle={() => setFollowing(!following)} />
          </Cell>
          <Cell label="Following">
            <FollowButton following onToggle={() => {}} />
          </Cell>
        </Block>

        <Block name="PersonRow" use="Someone in a list of people: search, followers, following.">
          <Cell label="With a display name">
            <PersonRow username="ana_p" displayName="Ana Popescu" />
          </Cell>
          <Cell label="Username only">
            <PersonRow username="radu" displayName={null} />
          </Cell>
          <Cell label="Loading">
            <div className="w-full">
              <PersonRowSkeleton />
            </div>
          </Cell>
        </Block>

        <Block name="FeedRow" use="One friend's rating in the feed. Small cover, never the record.">
          <div className="col-span-full flex max-w-prose flex-col divide-y divide-border">
            <FeedRow
              name="Ana"
              username="ana_p"
              albumId={ok.mbid}
              title={ok.title}
              artist={ok.artist}
              coverUrl={coverUrl(ok.mbid, 250)}
              rating={4.5}
              body="The best one, no debate. Let Down still gets me every time, and the second half is the reason I keep coming back to it on long drives."
              at="2026-10-08T10:00:00Z"
              when="3h"
            />
            <FeedRow
              name="Radu"
              username="radu"
              albumId={kid.mbid}
              title={kid.title}
              artist={kid.artist}
              coverUrl={coverUrl(kid.mbid, 250)}
              rating={3}
              body={null}
              at="2026-10-01T10:00:00Z"
              when="1 Oct 2026"
            />
            <FeedRow
              name="Someone"
              username={null}
              albumId={ok.mbid}
              title="Songs for the Night Bus"
              artist="Unknown"
              rating={2}
              body="No cover, and the account is gone."
              at="2026-10-08T10:00:00Z"
              when="5m"
            />
            <FeedRowSkeleton />
          </div>
        </Block>

        <Block name="Skeleton" use="Loading. Shaped like the content it stands in for; pulses in place.">
          <Cell label="Line, block, circle">
            <div className="flex items-center gap-4">
              <Skeleton className="h-3 w-16" />
              <Skeleton shape="block" className="size-12" />
              <Skeleton shape="circle" className="size-avatar" />
            </div>
          </Cell>
          <Cell label="Text: inside a line, keeps its height">
            {/* Full width, like a real line of text: fractions resolve against the line. */}
            <p className="w-full text-body text-muted">
              <Skeleton shape="text" className="w-1/4" /> rated <Skeleton shape="text" className="w-1/3" />
            </p>
          </Cell>
          <Cell label="Review row">
            <div className="flex w-full gap-3">
              <Skeleton shape="circle" className="size-avatar" />
              <div className="flex w-full flex-col gap-2">
                <Skeleton className="h-3 w-1/3" />
                <Skeleton className="h-3 w-full" />
                <Skeleton className="h-3 w-2/3" />
              </div>
            </div>
          </Cell>
          <Cell label="Cover grid (AlbumTileSkeleton)">
            <div className="grid w-full grid-cols-3 gap-3">
              {[0, 1, 2].map((i) => (
                <AlbumTileSkeleton key={i} />
              ))}
            </div>
          </Cell>
        </Block>

        <Block name="EmptyState" use="A list with nothing in it: one line, one action.">
          <Cell label="With action">
            <EmptyState
              message="Your crate is empty. Save albums to listen to later."
              action={<Button variant="primary">Search albums</Button>}
            />
          </Cell>
          <Cell label="Line only">
            <EmptyState message="No records in this crate. Try another name." />
          </Cell>
        </Block>

        <Block name="ErrorState" use="Something failed to load: what happened, and Try again.">
          <Cell label="Default">
            <ErrorState message="Couldn't load reviews." onRetry={() => {}} />
          </Cell>
          <Cell label="Retrying">
            <ErrorState message="Couldn't load the feed." onRetry={() => {}} retrying />
          </Cell>
        </Block>

        <Block name="Toast" use="Confirming an action that changed something. One at a time.">
          <Cell label="Message">
            <ToastMessage>Added to listen later</ToastMessage>
          </Cell>
          <Cell label="Live">
            <Button onClick={() => toast.show('Sent to Ana')}>Show a toast</Button>
          </Cell>
        </Block>

        <Block name="AppShell" use="The frame of every page. This page sits inside it.">
          <Cell label="Active section">
            <div className="flex rounded-control border border-border-strong p-1" role="group" aria-label="Active section">
              {sections.map((section) => (
                <button
                  key={section}
                  type="button"
                  aria-pressed={tab === section}
                  onClick={() => setTab(section)}
                  className={`flex h-tap flex-1 items-center justify-center rounded-control px-3 text-body ${
                    tab === section ? 'bg-surface font-medium text-text' : 'text-muted'
                  }`}
                >
                  {sectionNames[section]}
                </button>
              ))}
            </div>
          </Cell>
        </Block>
      </main>
      <ToastRegion message={toast.message} />
    </AppShell>
  )
}

/** One component: its name and when to use it, then its states. */
function Block({ name, use, children }: { name: string; use: string; children: ReactNode }) {
  return (
    <section aria-labelledby={`c-${name}`} className="grid gap-6 border-b border-border py-8 lg:grid-cols-4 lg:gap-8">
      <div className="flex flex-col gap-1">
        <h2 id={`c-${name}`} className="font-display text-h4 font-semibold text-text">
          {name}
        </h2>
        <p className="text-meta text-muted">{use}</p>
      </div>
      <div className="grid gap-6 sm:grid-cols-2 lg:col-span-3 lg:gap-8">{children}</div>
    </section>
  )
}

function Cell({ label, children }: { label: string; children: ReactNode }) {
  return (
    <div className="flex min-w-0 flex-col items-start gap-3">
      <p className="text-meta text-muted">{label}</p>
      {children}
    </div>
  )
}

/** Room above for the readout, width of the album page sidebar. */
function HistogramFrame({ counts }: { counts: number[] }) {
  return (
    <div className="w-full max-w-panel pt-12">
      <Histogram counts={counts} />
    </div>
  )
}

function sentence(word: string) {
  return word[0].toUpperCase() + word.slice(1)
}
