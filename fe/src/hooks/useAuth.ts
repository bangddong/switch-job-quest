const TOKEN_KEY = 'devquest-token'
export const GITHUB_REDIRECT_URI = `${window.location.origin}/auth/callback`

export function getToken(): string | null {
  return localStorage.getItem(TOKEN_KEY)
}

export function setToken(token: string): void {
  localStorage.setItem(TOKEN_KEY, token)
}

export function clearToken(): void {
  localStorage.removeItem(TOKEN_KEY)
}

export function useAuth() {
  const token = getToken()
  const isLoggedIn = token !== null

  const loginWithGithub = () => {
    const clientId = import.meta.env.VITE_GITHUB_CLIENT_ID
    const redirectUri = encodeURIComponent(GITHUB_REDIRECT_URI)
    window.location.href = `https://github.com/login/oauth/authorize?client_id=${clientId}&redirect_uri=${redirectUri}&scope=read:user`
  }

  const logout = () => {
    clearToken()
    window.location.reload()
  }

  return { isLoggedIn, loginWithGithub, logout }
}
