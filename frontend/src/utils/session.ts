const SESSION_KEY = 'qrwait_session'

interface WaitingSession {
  waitingId: string
  waitingToken: string
  storeId: string
  waitingNumber: number
}

export const saveWaitingSession = (
    waitingId: string,
    waitingToken: string,
    storeId: string,
    waitingNumber: number,
) => {
  localStorage.setItem(SESSION_KEY, JSON.stringify({waitingId, waitingToken, storeId, waitingNumber}))
}

export const getWaitingSession = (): WaitingSession | null => {
  const raw = localStorage.getItem(SESSION_KEY)
  if (!raw) return null
  try {
    return JSON.parse(raw) as WaitingSession
  } catch {
    return null
  }
}

export const clearWaitingSession = () => {
  localStorage.removeItem(SESSION_KEY)
}
