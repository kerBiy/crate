// Response shapes, mirroring the services' records.

/** user-service MeResponse. */
export type Me = {
  id: string
  username: string
  email: string
  displayName: string | null
  createdAt: string
}

/** user-service TokenResponse. */
export type Token = { accessToken: string; expiresAt: string }

/** catalog-service AlbumSummary: one album in a list. */
export type AlbumSummary = {
  id: string
  title: string
  artistCredit: string
  year: string | null
  coverUrl: string
  avgRating: number | null
  ratingCount: number
}

/** catalog-service AlbumDetails: the album page. */
export type AlbumDetails = AlbumSummary & {
  primaryArtistId: string | null
  primaryType: string | null
  /** MusicBrainz dates can be partial: "1997", "1997-05" or "1997-05-21". */
  firstReleaseDate: string | null
}

/** catalog-service SearchResponse. partial: MusicBrainz couldn't be asked, results may be missing. */
export type SearchResponse = { items: AlbumSummary[]; partial: boolean }
