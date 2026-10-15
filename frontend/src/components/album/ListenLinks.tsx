import { useAlbumTracks } from '../../api/queries.ts'
import type { ListenLink } from '../../api/types.ts'
import appleMusicIcon from '../../assets/listen/apple-music.svg'
import spotifyIcon from '../../assets/listen/spotify.png'
import youtubeMusicIcon from '../../assets/listen/youtube-music.png'
import { Skeleton } from '../ui/Skeleton.tsx'
import { Tooltip } from '../ui/Tooltip.tsx'

/** The three platforms the album page shows, in this order. The icons are the brands' own (ADR-015). */
const platforms: { id: ListenLink['platform']; name: string; icon: string; search: (query: string) => string }[] = [
  {
    id: 'SPOTIFY',
    name: 'Spotify',
    icon: spotifyIcon,
    search: (query) => `https://open.spotify.com/search/${encodeURIComponent(query)}`,
  },
  {
    id: 'APPLE_MUSIC',
    name: 'Apple Music',
    icon: appleMusicIcon,
    search: (query) => `https://music.apple.com/search?term=${encodeURIComponent(query)}`,
  },
  {
    id: 'YOUTUBE_MUSIC',
    name: 'YouTube Music',
    icon: youtubeMusicIcon,
    search: (query) => `https://music.youtube.com/search?q=${encodeURIComponent(query)}`,
  },
]

/**
 * Where to listen, under the cover (DESIGN.md section 8). The links come with the tracklist, so this
 * needs no request of its own.
 */
export function ListenLinks({ albumId, title, artist }: { albumId: string; title: string; artist: string }) {
  const tracks = useAlbumTracks(albumId)
  // A failed tracklist leaves no exact links: every icon searches.
  const links = tracks.isPending ? 'loading' : (tracks.data?.listenLinks ?? [])
  return <ListenIcons links={links} title={title} artist={artist} />
}

/**
 * Spotify, Apple Music and YouTube Music, always all three. A platform with an exact link opens the album
 * there ("Open on Spotify"); otherwise its search for "artist title" ("Search on Spotify"). While the
 * links load, three placeholders of the same size hold the row.
 */
export function ListenIcons({ links, title, artist }: { links: ListenLink[] | 'loading'; title: string; artist: string }) {
  if (links === 'loading') {
    return (
      <div aria-hidden="true" className="flex gap-4">
        {platforms.map((platform) => (
          <Skeleton key={platform.id} shape="circle" className="size-avatar" />
        ))}
      </div>
    )
  }
  const exact = new Map(links.map((link) => [link.platform, link.url]))
  return (
    // isolate: the tap areas' pressed backgrounds sit behind the icons, inside this row.
    <ul aria-label="Where to listen" className="isolate flex gap-4">
      {platforms.map((platform) => {
        const url = exact.get(platform.id)
        const label = `${url ? 'Open' : 'Search'} on ${platform.name}`
        return (
          <li key={platform.id} className="flex">
            <Tooltip text={label}>
              <a
                href={url ?? platform.search(`${artist} ${title}`)}
                target="_blank"
                rel="noopener noreferrer"
                className="tap-area flex size-avatar rounded-full"
              >
                <img src={platform.icon} alt="" width={32} height={32} className="size-avatar" />
                <span className="sr-only">{label}, opens in a new tab</span>
              </a>
            </Tooltip>
          </li>
        )
      })}
    </ul>
  )
}
