import js from '@eslint/js'
import tseslint from 'typescript-eslint'
import reactHooks from 'eslint-plugin-react-hooks'
import globals from 'globals'

export default tseslint.config(
  { ignores: ['dist'] },

  js.configs.recommended,
  ...tseslint.configs.recommended,

  {
    languageOptions: {
      globals: globals.browser,
    },
    plugins: {
      'react-hooks': reactHooks,
    },
    rules: {
      // react hooks
      'react-hooks/rules-of-hooks': 'error',
      'react-hooks/exhaustive-deps': 'warn',

      // [1. 코드 어시스턴트] named export 강제
      'no-restricted-syntax': [
        'error',
        {
          selector: 'ExportDefaultDeclaration',
          message: 'export default 금지. named export를 사용하세요.',
        },
      ],

      // [1. 코드 어시스턴트] 외부 상태관리 금지
      'no-restricted-imports': [
        'error',
        {
          patterns: [
            { group: ['redux', '@reduxjs/*'], message: 'Redux 사용 금지. useState를 사용하세요.' },
            { group: ['zustand'], message: 'Zustand 사용 금지. useState를 사용하세요.' },
            { group: ['jotai', 'recoil', 'mobx*'], message: '외부 상태관리 사용 금지. useState를 사용하세요.' },
          ],
        },
      ],
    },
  },
)
