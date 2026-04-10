import axios from 'axios'
import ownerClient from './ownerClient'
import useOwnerStore from '../store/ownerStore'

export interface SignUpRequest {
  email: string
  password: string
  storeName: string
  address: string
}

export interface SignUpResponse {
  ownerId: string
  storeId: string
  qrUrl: string
}

export interface LoginRequest {
  email: string
  password: string
}

export interface LoginResponse {
  accessToken: string
  ownerId: string
  storeId: string
}

export const signUp = (body: SignUpRequest): Promise<SignUpResponse> =>
    axios.post('/api/auth/signup', body).then((res) => res.data)

export const login = (body: LoginRequest): Promise<LoginResponse> =>
    axios.post('/api/auth/login', body, {withCredentials: true}).then((res) => res.data)

export const logout = (): Promise<void> =>
    ownerClient.post('/auth/logout').then(() => {
      useOwnerStore.getState().clearAuth()
    })

export const refreshToken = (): Promise<{ accessToken: string; ownerId: string; storeId: string }> =>
    axios.post('/api/auth/refresh', {}, {withCredentials: true}).then((res) => res.data)

export interface UpdateStoreSettingsRequest {
  tableCount: number
  avgTurnoverMinutes: number
  openTime: string | null
  closeTime: string | null
  alertThreshold: number
  alertEnabled: boolean
}

export const updateStoreSettings = (body: UpdateStoreSettingsRequest): Promise<void> =>
    ownerClient.put('/owner/stores/me/settings', body).then((res) => res.data)

export type StoreStatus = 'OPEN' | 'BREAK' | 'FULL' | 'CLOSED'

export interface MyStoreResponse {
  storeId: string
  name: string
  address: string
  status: StoreStatus
}

export interface OwnerWaitingItem {
  waitingId: string
  waitingNumber: number
  visitorName: string
  partySize: number
  status: 'WAITING' | 'CALLED'
  elapsedMinutes: number
}

export interface DailySummary {
  totalRegistered: number
  totalEntered: number
  totalNoShow: number
  totalCancelled: number
  currentWaiting: number
}

export const getMyStore = (): Promise<MyStoreResponse> =>
    ownerClient.get('/owner/stores/me').then((res) => res.data)

export const getWaitingList = (): Promise<OwnerWaitingItem[]> =>
    ownerClient.get('/owner/stores/me/waitings').then((res) => res.data)

export const getDailySummary = (): Promise<DailySummary> =>
    ownerClient.get('/owner/stores/me/waitings/summary').then((res) => res.data)

export const updateStoreStatus = (status: StoreStatus): Promise<void> =>
    ownerClient.put('/owner/stores/me/status', {status}).then((res) => res.data)

export const callWaiting = (waitingId: string): Promise<void> =>
    ownerClient.post(`/owner/waitings/${waitingId}/call`).then((res) => res.data)

export const enterWaiting = (waitingId: string): Promise<void> =>
    ownerClient.post(`/owner/waitings/${waitingId}/enter`).then((res) => res.data)

export const noShowWaiting = (waitingId: string): Promise<void> =>
    ownerClient.post(`/owner/waitings/${waitingId}/noshow`).then((res) => res.data)
