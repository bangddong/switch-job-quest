import { describe, it, expect } from 'vitest'
import {
  normalizeExtractedText,
  truncateExtractedText,
  validatePdfFile,
  PdfExtractError,
} from './extractPdfText'

// 소스의 MAX_TEXT_LENGTH(50000)는 export되지 않으므로 테스트에서 명시적으로 다룬다.
const MAX_TEXT_LENGTH = 50000
// 소스의 MAX_FILE_SIZE(5MB)도 동일한 이유로 명시적으로 다룬다.
const MAX_FILE_SIZE = 5 * 1024 * 1024

describe('normalizeExtractedText', () => {
  it('CRLF(\\r\\n)를 \\n으로 변환한다', () => {
    expect(normalizeExtractedText('a\r\nb')).toBe('a\nb')
  })

  it('lone CR(\\r)을 \\n으로 변환한다', () => {
    expect(normalizeExtractedText('a\rb')).toBe('a\nb')
  })

  it('연속 공백/탭(개행 제외)을 단일 스페이스로 축약한다', () => {
    expect(normalizeExtractedText('a\t\t  b')).toBe('a b')
  })

  it('개행 3개 이상을 정확히 2개로 축약한다', () => {
    expect(normalizeExtractedText('a\n\n\n\nb')).toBe('a\n\nb')
  })

  it('앞뒤 공백을 trim한다', () => {
    expect(normalizeExtractedText('  a b  ')).toBe('a b')
  })

  it('이미 정규화된 텍스트는 그대로 유지한다 (idempotent)', () => {
    expect(normalizeExtractedText('a\n\nb')).toBe('a\n\nb')
  })

  it('빈 문자열은 빈 문자열을 반환한다', () => {
    expect(normalizeExtractedText('')).toBe('')
  })
})

describe('truncateExtractedText', () => {
  it('길이가 정확히 50000이면 잘리지 않고 원문 그대로 반환한다', () => {
    const text = 'a'.repeat(MAX_TEXT_LENGTH)
    const result = truncateExtractedText(text)
    expect(result.truncated).toBe(false)
    expect(result.text).toBe(text)
  })

  it('길이가 50001이면 잘리고 50000자로 줄어든다', () => {
    const text = 'a'.repeat(MAX_TEXT_LENGTH + 1)
    const result = truncateExtractedText(text)
    expect(result.truncated).toBe(true)
    expect(result.text.length).toBe(MAX_TEXT_LENGTH)
  })

  it('짧은 문자열은 잘리지 않는다', () => {
    const result = truncateExtractedText('short text')
    expect(result.truncated).toBe(false)
    expect(result.text).toBe('short text')
  })
})

describe('validatePdfFile', () => {
  it('application/pdf 타입이면 null을 반환한다', () => {
    const file = new File(['content'], 'resume.pdf', { type: 'application/pdf' })
    expect(validatePdfFile(file)).toBeNull()
  })

  it('타입이 빈 문자열이어도 .pdf 확장자면 null을 반환한다', () => {
    const file = new File(['content'], 'resume.pdf', { type: '' })
    expect(validatePdfFile(file)).toBeNull()
  })

  it('대문자 .PDF 확장자도 null을 반환한다', () => {
    const file = new File(['content'], 'resume.PDF', { type: '' })
    expect(validatePdfFile(file)).toBeNull()
  })

  it('타입·확장자 둘 다 PDF가 아니면 타입 에러 메시지를 반환한다', () => {
    const file = new File(['content'], 'resume.txt', { type: 'text/plain' })
    expect(validatePdfFile(file)).toBe('PDF 파일만 업로드할 수 있습니다.')
  })

  it('5MB를 초과하면 크기 에러 메시지를 반환한다', () => {
    const file = new File(['content'], 'resume.pdf', { type: 'application/pdf' })
    Object.defineProperty(file, 'size', { value: MAX_FILE_SIZE + 1 })
    expect(validatePdfFile(file)).toBe('파일 크기는 5MB를 넘을 수 없습니다.')
  })
})

describe('PdfExtractError', () => {
  it('name이 PdfExtractError이고 Error의 인스턴스다', () => {
    const error = new PdfExtractError('테스트 메시지')
    expect(error.name).toBe('PdfExtractError')
    expect(error).toBeInstanceOf(Error)
  })

  it('options.cause를 전달하면 .cause에 보존한다', () => {
    const cause = new Error('원인 에러')
    const error = new PdfExtractError('테스트 메시지', { cause })
    expect(error.cause).toBe(cause)
  })

  it('options를 전달하지 않으면 .cause는 undefined다', () => {
    const error = new PdfExtractError('테스트 메시지')
    expect(error.cause).toBeUndefined()
  })
})
