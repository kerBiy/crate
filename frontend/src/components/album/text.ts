const months = ['January', 'February', 'March', 'April', 'May', 'June', 'July', 'August', 'September', 'October', 'November', 'December']

/** MusicBrainz dates can be partial: "1997" → "1997", "1997-05" → "May 1997", "1997-05-21" → "21 May 1997". */
export function releaseDate(date: string | null) {
  if (!date) return null
  const [year, month, day] = date.split('-')
  const monthName = month ? months[Number(month) - 1] : undefined
  if (!monthName) return year
  return day ? `${Number(day)} ${monthName} ${year}` : `${monthName} ${year}`
}

/** MusicBrainz writes genres in lowercase; a tag starts with a capital like any other label. */
export function sentence(text: string) {
  return text.charAt(0).toUpperCase() + text.slice(1)
}
