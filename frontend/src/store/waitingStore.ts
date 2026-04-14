import {create} from 'zustand'

interface WaitingState {
  waitingId: string | null
  waitingNumber: number | null
  storeId: string | null
  currentRank: number | null
  totalWaiting: number | null
  estimatedWaitMinutes: number | null
}

interface WaitingActions {
  setWaiting: (payload: {
    waitingId: string
    waitingNumber: number
    storeId: string
    currentRank: number
    totalWaiting: number
    estimatedWaitMinutes: number
  }) => void
  updateStatus: (payload: {
    currentRank: number
    totalWaiting: number
    estimatedWaitMinutes: number
  }) => void
  clearWaiting: () => void
}

const initialState: WaitingState = {
  waitingId: null,
  waitingNumber: null,
  storeId: null,
  currentRank: null,
  totalWaiting: null,
  estimatedWaitMinutes: null,
}

const useWaitingStore = create<WaitingState & WaitingActions>((set) => ({
  ...initialState,

  setWaiting: (payload) => set(payload),

  updateStatus: (payload) => set(payload),

  clearWaiting: () => set(initialState),
}))

export default useWaitingStore
