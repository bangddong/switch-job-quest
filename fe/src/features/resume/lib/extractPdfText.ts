const MAX_FILE_SIZE = 5 * 1024 * 1024
const MAX_TEXT_LENGTH = 50000

export class PdfExtractError extends Error {}

/** 연속 공백/개행을 정리한다 (순수 함수, 테스트 용이) */
export function normalizeExtractedText(raw: string): string {
  return raw
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
  const pdfjs = await import('pdfjs-dist/legacy/build/pdf.mjs')
  const workerUrl = (await import('pdfjs-dist/legacy/build/pdf.worker.min.mjs?url')).default
  pdfjs.GlobalWorkerOptions.workerSrc = workerUrl

  const data = await file.arrayBuffer()
  const doc = await pdfjs.getDocument({ data }).promise

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
  } finally {
    await doc.destroy()
  }

  const normalized = normalizeExtractedText(pageTexts.join('\n\n'))

  if (!normalized) {
    throw new PdfExtractError(
      '텍스트를 추출할 수 없습니다. 스캔된 이미지 PDF는 지원하지 않아요. 내용을 직접 붙여넣어 주세요.',
    )
  }

  return normalized
}
