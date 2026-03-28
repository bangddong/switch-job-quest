export type CharacterRole = '백엔드' | '프론트엔드' | '풀스택' | '데이터/ML' | 'DevOps'
export type CharacterYears = '신입' | '1-3년' | '3-5년' | '5년+'

export interface Character {
  nickname: string
  role: CharacterRole
  years: CharacterYears
}
