import js from '@eslint/js'
import tseslint from 'typescript-eslint'
import reactHooks from 'eslint-plugin-react-hooks'
import globals from 'globals'

export default tseslint.config(
  { ignores: ['dist'] },

  js.configs.recommended,
  ...tseslint.configs.recommended,

  // 전역 변수는 실행 환경별로 분리한다 — 브라우저(src)와 Workers(worker)가 섞이지 않게.
  {
    files: ['src/**/*.{ts,tsx}'],
    languageOptions: {
      globals: globals.browser,
    },
  },
  {
    files: ['worker/**/*.ts'],
    languageOptions: {
      globals: globals.serviceworker,
    },
  },

  {
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

  // Workers 모듈 형식은 엔트리 파일의 default export 를 요구한다. 이 파일 하나만 예외.
  // (no-restricted-syntax 는 위에서 export default 금지 한 가지 셀렉터만 갖고 있다.)
  {
    files: ['worker/index.ts'],
    rules: {
      'no-restricted-syntax': 'off',
    },
  },
)
