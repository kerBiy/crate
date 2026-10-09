import { SegmentedControl } from '../components/ui/SegmentedControl.tsx'
import { useTheme } from '../theme.ts'

/** Dark / Light switch for the dev tools. The app's own setting, with Match system, is in Settings. */
export function ThemeToggle() {
  const { choice, choose } = useTheme()
  return (
    <SegmentedControl
      label="Theme"
      options={[
        { id: 'dark', label: 'Dark' },
        { id: 'light', label: 'Light' },
      ]}
      value={choice === 'light' ? 'light' : 'dark'}
      onChange={choose}
    />
  )
}
