import axios from 'axios'
import client from './client'
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

// signUp·login은 client(공개 axios 인스턴스)를 거쳐야 인터셉터가 서버 에러 메시지를
// 한글로 매핑해준다. raw axios로 직접 호출하면 "Request failed with status code 401" 같은
// axios 원본 문자열이 그대로 화면에 노출된다.
export const signUp = (body: SignUpRequest): Promise<SignUpResponse> =>
    client.post('/auth/signup', body).then((res) => res.data)

export const login = (body: LoginRequest): Promise<LoginResponse> =>
    client.post('/auth/login', body, {withCredentials: true}).then((res) => res.data)

export const logout = (): Promise<void> =>
    ownerClient.post('/auth/logout').then(() => {
      useOwnerStore.getState().clearAuth()
    })

export const refreshToken = (): Promise<{ accessToken: string; ownerId: string; storeId: string }> =>
    axios.post('/api/auth/refresh', {}, {withCredentials: true}).then((res) => res.data)

export interface UpdateStoreSettingsRequest {
  tableCount: number
  avgTurnoverMinutes: number
  openTime: string
  closeTime: string | null
  alertThreshold: number
  alertEnabled: boolean
  callGraceMinutes: number
}

export interface StoreSettingsResponse {
  tableCount: number
  avgTurnoverMinutes: number
  openTime: string
  closeTime: string | null
  alertThreshold: number
  alertEnabled: boolean
  estimatedWaitFormulaExample: string
  callGraceMinutes: number
}

export const getStoreSettings = (): Promise<StoreSettingsResponse> =>
    ownerClient.get('/owner/stores/me/settings').then((res) => res.data)

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
  phoneNumber: string
  partySize: number
  status: 'WAITING' | 'CALLED'
  createdAt: string
  graceDeadline: string | null
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

export const postponeWaiting = (waitingId: string): Promise<void> =>
    ownerClient.post(`/owner/waitings/${waitingId}/postpone`).then((res) => res.data)

export interface TodayWaiting {
  waitingId: string
  waitingNumber: number
  phoneNumber: string
  partySize: number
  status: 'WAITING' | 'CALLED' | 'ENTERED' | 'NO_SHOW' | 'CANCELLED'
  createdAt: string
  waitedMinutes: number | null
}

export const getTodayWaitings = (date?: string): Promise<TodayWaiting[]> =>
    ownerClient.get('/owner/stores/me/waitings/today', {params: date ? {date} : undefined})
        .then((res) => res.data)
