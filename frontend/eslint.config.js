import js from '@eslint/js'
import reactHooks from 'eslint-plugin-react-hooks'
import { defineConfig, globalIgnores } from 'eslint/config'
import tseslint from 'typescript-eslint'

// DESIGN.md section 4: components use design tokens only. Catches arbitrary Tailwind values
// (w-[13px]), dark: variants and raw hex colors in component strings.
const tokenOnly = '/\\w-\\[|\\bdark:|#[0-9a-fA-F]{3,8}\\b/'
const tokenMessage = 'Use design tokens: no arbitrary Tailwind values, dark: variants or raw hex (docs/DESIGN.md).'

export default defineConfig([
  globalIgnores(['dist']),
  {
    files: ['**/*.{ts,tsx}'],
    extends: [js.configs.recommended, tseslint.configs.recommended, reactHooks.configs.flat.recommended],
  },
  {
    files: ['src/**/*.tsx'],
    rules: {
      'no-restricted-syntax': [
        'error',
        { selector: `Literal[value=${tokenOnly}]`, message: tokenMessage },
        { selector: `TemplateElement[value.raw=${tokenOnly}]`, message: tokenMessage },
      ],
    },
  },
])
