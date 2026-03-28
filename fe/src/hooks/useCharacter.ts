import { useState } from 'react'
import type { Character } from '@/types/character.types'

const STORAGE_KEY = 'devquest-character'

function loadCharacter(): Character | null {
  try {
    const raw = localStorage.getItem(STORAGE_KEY)
    return raw ? (JSON.parse(raw) as Character) : null
  } catch {
    return null
  }
}

function saveCharacter(character: Character): void {
  try {
    localStorage.setItem(STORAGE_KEY, JSON.stringify(character))
  } catch {
    // ignore
  }
}

export function useCharacter() {
  const [character, setCharacterState] = useState<Character | null>(loadCharacter)

  const setCharacter = (c: Character) => {
    saveCharacter(c)
    setCharacterState(c)
  }

  return { character, setCharacter }
}
