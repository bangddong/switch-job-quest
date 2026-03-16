export type QuestType = 'STUDY' | 'WRITE' | 'DISCOVER' | 'BUILD' | 'BOSS'

export interface Quest {
  id: string
  type: QuestType
  title: string
  xp: number
  aiCheck: boolean
  tag: string
  difficulty: number
  desc: string
  tasks: string[]
}

export interface Act {
  id: number
  title: string
  subtitle: string
  color: string
  icon: string
  quests: Quest[]
}

export interface QuestTypeConfig {
  bg: string
  border: string
  icon: string
}
