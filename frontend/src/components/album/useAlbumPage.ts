import { useCallback, useState } from 'react'
import { useToast } from '../ui/Toast.tsx'

/** A flag that turns on for one CSS animation and off when it ends. */
export function useOneShot() {
  const [on, setOn] = useState(false)
  const play = useCallback(() => setOn(true), [])
  const done = useCallback(() => setOn(false), [])
  return { on, play, done }
}

/** Local state for the album page actions: rate, listen later, send. Nothing is saved. */
export function useAlbumPage() {
  const [rating, setRating] = useState(0)
  const [listenLater, setListenLater] = useState(false)
  const toast = useToast()
  const spin = useOneShot()
  const send = useOneShot()
  const nudge = useOneShot()

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
      toast.show(next ? 'Added to listen later' : 'Removed from listen later')
    },
    sendTo(name: string) {
      send.play()
      toast.show(`Sent to ${name}`)
    },
    toast,
    spin,
    send,
    nudge,
  }
}

export type AlbumPageState = ReturnType<typeof useAlbumPage>
