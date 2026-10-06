// Static data for the design lab. MBIDs are real MusicBrainz release-group IDs; covers come
// from the Cover Art Archive. dominantColor was sampled once from each cover, standing in for
// the dominant_color field catalog-service will store later.

import type { Friend } from '../components/album/bits.tsx'
import type { Review } from '../components/album/Reviews.tsx'

export type Album = {
  mbid: string
  title: string
  artist: string
  year: number
  released: string
  length: string
  tracks: number
  label: string
  dominantColor: string
  average: number
  ratingCount: number
  // Number of ratings per half-star bucket: 0.5, 1, 1.5 … 5.
  histogram: number[]
  friends: Friend[]
  reviews: Review[]
}

export const albums: Album[] = [
  {
    mbid: 'b1392450-e666-3926-a536-22c65f834433',
    title: 'OK Computer',
    artist: 'Radiohead',
    year: 1997,
    released: '21 May 1997',
    length: '53 min',
    tracks: 12,
    label: 'Parlophone',
    dominantColor: '#9FBBCB',
    average: 4.4,
    ratingCount: 128,
    histogram: [0, 1, 0, 2, 3, 6, 11, 24, 33, 48],
    friends: [
      { name: 'Ana', rating: 5 },
      { name: 'Mihai', rating: 4.5 },
      { name: 'Ioana', rating: 4 },
      { name: 'Radu', rating: 5 },
      { name: 'Sam', rating: 3.5 },
      { name: 'Lena', rating: 4.5 },
      { name: 'Tudor', rating: 4 },
    ],
    reviews: [
      {
        name: 'Ana',
        rating: 5,
        text: 'The best one, no debate. Let Down still gets me every single time.',
        when: '2 days ago',
      },
      {
        name: 'Radu',
        rating: 5,
        text: 'Listened on a night train and it finally made sense. Airbag into Paranoid Android is a perfect opening.',
        when: '1 week ago',
      },
      {
        name: 'Sam',
        rating: 3.5,
        text: 'I respect it more than I love it. Fitter Happier is a skip for me.',
        when: '3 weeks ago',
      },
    ],
  },
  {
    mbid: '42d725fb-a8b7-388c-8866-3b02789af326',
    title: 'Blue',
    artist: 'Joni Mitchell',
    year: 1971,
    released: '22 June 1971',
    length: '36 min',
    tracks: 10,
    label: 'Reprise',
    dominantColor: '#26336B',
    average: 4.6,
    ratingCount: 74,
    histogram: [0, 0, 0, 1, 1, 2, 4, 9, 20, 37],
    friends: [
      { name: 'Ioana', rating: 5 },
      { name: 'Lena', rating: 5 },
      { name: 'Ana', rating: 4.5 },
      { name: 'Maya', rating: 4 },
    ],
    reviews: [
      {
        name: 'Lena',
        rating: 5,
        text: 'Thirty-six minutes and not a wasted second. A Case of You is the whole album in one song.',
        when: '4 days ago',
      },
      {
        name: 'Ioana',
        rating: 5,
        text: 'Put it on for a rainy Sunday, ended up playing it three times.',
        when: '2 weeks ago',
      },
    ],
  },
  {
    mbid: '8e8a594f-2175-38c7-a871-abb68ec363e7',
    title: 'Kind of Blue',
    artist: 'Miles Davis',
    year: 1959,
    released: '17 August 1959',
    length: '46 min',
    tracks: 5,
    label: 'Columbia',
    dominantColor: '#4E6B66',
    average: 4.5,
    ratingCount: 96,
    histogram: [0, 0, 1, 1, 2, 4, 8, 15, 27, 38],
    friends: [
      { name: 'Tudor', rating: 5 },
      { name: 'Mihai', rating: 4.5 },
      { name: 'Sam', rating: 4.5 },
      { name: 'Radu', rating: 4 },
      { name: 'Maya', rating: 5 },
    ],
    reviews: [
      {
        name: 'Tudor',
        rating: 5,
        text: 'Where I tell people to start with jazz. So What sounds like it was recorded yesterday.',
        when: '5 days ago',
      },
      {
        name: 'Mihai',
        rating: 4.5,
        text: 'Blue in Green at 2 a.m. is a different record than at noon.',
        when: '1 month ago',
      },
      {
        name: 'Maya',
        rating: 5,
        text: 'My dad played this every Saturday morning. Still the sound of weekends.',
        when: '2 months ago',
      },
    ],
  },
  {
    mbid: '0f1b9e07-b38b-4bba-9794-55e0924d7177',
    title: 'IGOR',
    artist: 'Tyler, the Creator',
    year: 2019,
    released: '17 May 2019',
    length: '40 min',
    tracks: 12,
    label: 'Columbia',
    dominantColor: '#F0AFC3',
    average: 4.1,
    ratingCount: 52,
    histogram: [1, 0, 1, 1, 2, 4, 8, 13, 12, 10],
    friends: [
      { name: 'Sam', rating: 4.5 },
      { name: 'Maya', rating: 4 },
    ],
    reviews: [],
  },
  {
    mbid: '416bb5e5-c7d1-3977-8fd7-7c9daf6c2be6',
    title: 'Rumours',
    artist: 'Fleetwood Mac',
    year: 1977,
    released: '4 February 1977',
    length: '39 min',
    tracks: 11,
    label: 'Warner Bros.',
    dominantColor: '#E8DFC0',
    average: 4.3,
    ratingCount: 110,
    histogram: [0, 1, 1, 2, 4, 7, 14, 26, 30, 25],
    friends: [
      { name: 'Ana', rating: 4 },
      { name: 'Lena', rating: 4.5 },
      { name: 'Radu', rating: 3.5 },
      { name: 'Ioana', rating: 4.5 },
      { name: 'Tudor', rating: 4 },
      { name: 'Maya', rating: 5 },
    ],
    reviews: [
      {
        name: 'Maya',
        rating: 5,
        text: 'Everyone in the band was breaking up and it shows, in the best way. Dreams is untouchable.',
        when: '3 days ago',
      },
      {
        name: 'Radu',
        rating: 3.5,
        text: 'Great singles, a couple of songs I always forget are on it.',
        when: '3 weeks ago',
      },
    ],
  },
]

export const everyone = ['Ana', 'Mihai', 'Ioana', 'Radu', 'Sam', 'Lena', 'Tudor', 'Maya']

export function findAlbum(mbid: string | undefined) {
  return albums.find((album) => album.mbid === mbid)
}

export function coverUrl(mbid: string, size: 250 | 500) {
  return `https://coverartarchive.org/release-group/${mbid}/front-${size}`
}
