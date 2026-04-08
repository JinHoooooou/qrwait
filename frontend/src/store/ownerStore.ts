import {create} from 'zustand'

interface OwnerState {
  ownerId: string | null
  storeId: string | null
  accessToken: string | null
}

interface OwnerActions {
  setAuth: (payload: { ownerId: string; storeId: string; accessToken: string }) => void
  setAccessToken: (accessToken: string) => void
  clearAuth: () => void
}

const initialState: OwnerState = {
  ownerId: null,
  storeId: null,
  accessToken: null,
}

const useOwnerStore = create<OwnerState & OwnerActions>((set) => ({
  ...initialState,
  setAuth: (payload) => set(payload),
  setAccessToken: (accessToken) => set({accessToken}),
  clearAuth: () => set(initialState),
}))

export default useOwnerStore
