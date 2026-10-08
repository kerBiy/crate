import { Check } from '@phosphor-icons/react'
import { useState } from 'react'
import { Button } from './Button.tsx'

type FollowButtonProps = {
  following: boolean
  onToggle: () => void
  className?: string
}

/**
 * "Follow" (primary) or "Following" with a check (secondary). It pops once when it becomes
 * "Following" (DESIGN.md section 7); pressing "Following" unfollows. No pop with reduced motion.
 */
export function FollowButton({ following, onToggle, className = '' }: FollowButtonProps) {
  const [popping, setPopping] = useState(false)

  return (
    <Button
      variant={following ? 'secondary' : 'primary'}
      icon={following ? <Check size="1.25em" weight="bold" className="text-accent" aria-hidden="true" /> : undefined}
      onClick={() => {
        setPopping(!following)
        onToggle()
      }}
      onAnimationEnd={() => setPopping(false)}
      className={`${popping ? 'follow-pop' : ''} ${className}`}
    >
      {following ? 'Following' : 'Follow'}
    </Button>
  )
}
