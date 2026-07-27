const MAX_FILE_SIZE = 5 * 1024 * 1024
const MAX_TEXT_LENGTH = 50000

export class PdfExtractError extends Error {
  // lib ES2020에는 Error(message, { cause }) 시그니처가 타입 정의되어 있지 않아
  // super()에 options를 전달하지 못한다 — cause는 별도 필드로 보존한다.
  readonly cause?: unknown

  constructor(message: string, options?: { cause?: unknown }) {
    super(message)
    this.name = 'PdfExtractError'
    this.cause = options?.cause
  }
}

/** 연속 공백/개행을 정리한다 (순수 함수, 테스트 용이) */
export function normalizeExtractedText(raw: string): string {
  return raw
    .replace(/\r\n?/g, '\n')
    .replace(/[^\S\n]+/g, ' ')
    .replace(/\n{3,}/g, '\n\n')
    .trim()
}

/** 50,000자 초과 시 잘라내고 잘렸는지 여부를 함께 반환한다 (순수 함수) */
export function truncateExtractedText(text: string): { text: string; truncated: boolean } {
  if (text.length <= MAX_TEXT_LENGTH) return { text, truncated: false }
  return { text: text.slice(0, MAX_TEXT_LENGTH), truncated: true }
}

export function validatePdfFile(file: File): string | null {
  if (file.type !== 'application/pdf' && !file.name.toLowerCase().endsWith('.pdf')) {
    return 'PDF 파일만 업로드할 수 있습니다.'
  }
  if (file.size > MAX_FILE_SIZE) {
    return '파일 크기는 5MB를 넘을 수 없습니다.'
  }
  return null
}

/** 브라우저에서 PDF 텍스트를 추출한다. pdfjs-dist는 최초 호출 시점에만 지연 로드된다. */
export async function extractPdfText(file: File): Promise<string> {
  let pdfjs: typeof import('pdfjs-dist/legacy/build/pdf.mjs')
  try {
    pdfjs = await import('pdfjs-dist/legacy/build/pdf.mjs')
    const workerUrl = (await import('pdfjs-dist/legacy/build/pdf.worker.min.mjs?url')).default
    pdfjs.GlobalWorkerOptions.workerSrc = workerUrl
  } catch (e) {
    throw new PdfExtractError(
      'PDF 처리 모듈을 불러오지 못했어요. 잠시 후 다시 시도하거나 내용을 직접 붙여넣어 주세요.',
      { cause: e },
    )
  }

  let doc: Awaited<ReturnType<typeof pdfjs.getDocument>['promise']>
  try {
    const data = await file.arrayBuffer()
    doc = await pdfjs.getDocument({ data }).promise
  } catch (e) {
    throw new PdfExtractError(
      'PDF를 읽을 수 없어요. 손상되었거나 암호가 걸린 파일일 수 있어요. 내용을 직접 붙여넣어 주세요.',
      { cause: e },
    )
  }

  const pageTexts: string[] = []
  try {
    for (let pageNum = 1; pageNum <= doc.numPages; pageNum += 1) {
      const page = await doc.getPage(pageNum)
      const content = await page.getTextContent()
      let pageText = ''
      for (const item of content.items) {
        if (!('str' in item)) continue
        pageText += item.str
        pageText += item.hasEOL ? '\n' : ' '
      }
      pageTexts.push(pageText)
    }
  } catch (e) {
    if (e instanceof PdfExtractError) throw e
    throw new PdfExtractError(
      'PDF를 읽을 수 없어요. 손상되었거나 암호가 걸린 파일일 수 있어요. 내용을 직접 붙여넣어 주세요.',
      { cause: e },
    )
  } finally {
    try {
      await doc.destroy()
    } catch (e) {
      // cleanup 실패는 원 예외를 가리지 않도록 무시하되 디버깅 단서는 남긴다
      console.warn('PDF 리소스 정리 실패(무시하고 진행):', e)
    }
  }

  const normalized = normalizeExtractedText(pageTexts.join('\n\n'))

  if (!normalized) {
    throw new PdfExtractError(
      '텍스트를 추출할 수 없습니다. 스캔된 이미지 PDF는 지원하지 않아요. 내용을 직접 붙여넣어 주세요.',
    )
  }

  return normalized
}
