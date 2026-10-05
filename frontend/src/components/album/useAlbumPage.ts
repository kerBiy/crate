import { useCallback, useEffect, useRef, useState } from 'react'

/** A flag that turns on for one CSS animation and off when it ends. */
function useOneShot() {
  const [on, setOn] = useState(false)
  const play = useCallback(() => setOn(true), [])
  const done = useCallback(() => setOn(false), [])
  return { on, play, done }
}

/** Local state for the album page actions: rate, listen later, send. Nothing is saved. */
export function useAlbumPage() {
  const [rating, setRating] = useState(0)
  const [listenLater, setListenLater] = useState(false)
  const [toast, setToast] = useState<string | null>(null)
  const timer = useRef<number | undefined>(undefined)
  const spin = useOneShot()
  const send = useOneShot()
  const nudge = useOneShot()

  const showToast = useCallback((message: string) => {
    setToast(message)
    window.clearTimeout(timer.current)
    timer.current = window.setTimeout(() => setToast(null), 2400)
  }, [])

  useEffect(() => () => window.clearTimeout(timer.current), [])

  return {
    rating,
    rate(value: number) {
      setRating(value)
      spin.play()
    },
    listenLater,
    toggleListenLater() {
      const next = !listenLater
      setListenLater(next)
      if (next) nudge.play()
      showToast(next ? 'Added to listen later' : 'Removed from listen later')
    },
    sendTo(name: string) {
      send.play()
      showToast(`Sent to ${name}`)
    },
    toast,
    spin,
    send,
    nudge,
  }
}

export type AlbumPageState = ReturnType<typeof useAlbumPage>
